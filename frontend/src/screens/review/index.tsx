import { router, Stack } from 'expo-router';
import { useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { ApiError } from '@/api/client';
import {
  decideFeedback,
  fetchNext,
  submitAttempt,
  type Attempt,
  type Feedback,
  type Next,
  type Presentation,
  type SelfAssessment,
} from '@/api/practice';
import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { Chip } from '@/components/chip';
import { Notice } from '@/components/notice';
import { OptionRow } from '@/components/option-row';
import { ThemedText } from '@/components/themed-text';
import { questionTypeLabel } from '@/labels';
import { colors, components, spacing } from '@/theme';

import { AnswerInput, emptyAnswer, isAnswered, type Answer } from './answer-input';
import { ReviewComplete, type ItemResult, type Path } from './complete';

/** 첫 무도움 시도에 함께 받는 자기평가(스펙 §6.4.5). FSRS 등급은 판정과 이 값으로 서버가 정한다. */
const SELF_ASSESSMENT: { key: SelfAssessment; label: string }[] = [
  { key: 'RECALLED_EASILY', label: '쉽게 떠올렸어요' },
  { key: 'RECALLED_WITH_EFFORT', label: '힘들게 떠올렸거나 확신이 약해요' },
  { key: 'GUESSED', label: '떠올리지 못하고 추측했어요' },
];

/** 판정 결과. 보류(`uncertain`·`ambiguous`)면 맞음·틀림을 보여주지 않는다. */
type Outcome = 'correct' | 'wrong' | 'uncertain' | 'ambiguous';

/**
 * - loading: 다음 문제를 불러오는 중
 * - answering: 답하는 중. `retry`면 힌트 뒤 재도전이라 평가하지 않으므로 자기평가를 받지 않는다.
 * - checking: 답을 내고 서버가 다음 행동을 정하는 중
 * - result: 판정과 서버가 정한 다음 행동(힌트·개념 설명·다음 문제 등)을 보여준다
 */
type Phase =
  | { kind: 'loading' }
  | { kind: 'answering'; retry: boolean }
  | { kind: 'checking'; retry: boolean }
  | { kind: 'result'; attempt: Attempt; feedback: Feedback; outcome: Outcome }
  | { kind: 'done' };

function outcomeOf(attempt: Attempt, feedback: Feedback): Outcome {
  if (feedback.action === 'REQUEST_CONFIRMATION' || !attempt.judgment.judged) return 'uncertain';
  if (feedback.action === 'GENERATE_VARIANT' || attempt.judgment.verdict === 'UNABLE_TO_JUDGE') return 'ambiguous';
  return attempt.correct === true || attempt.judgment.verdict === 'MET' ? 'correct' : 'wrong';
}

/**
 * 화면에 남길 풀이 경로. 확인 문제는 원래 문제의 경로(`earlier`)를 마저 정한다.
 * 설명 뒤 확인 문제를 맞히면 설명 후 맞힘, 보류(변형 문제로 다시 묻기) 뒤 확인 문제를 맞히면 혼자 맞힘이다.
 */
function pathOf(outcome: Outcome, feedback: Feedback, earlier: ItemResult | undefined): Path {
  if (outcome === 'uncertain' || outcome === 'ambiguous') return 'held';
  if (outcome === 'wrong') return 'repeatedWrong';
  // 설명 없이 다시 물은 경우(매일 학습의 오늘 한 번 더)는 처음에 틀렸으므로 다시 볼 항목으로 둔다.
  if (earlier) return earlier.path === 'held' ? 'independent' : earlier.explained ? 'afterExplanation' : 'repeatedWrong';
  if (feedback.path === 'AFTER_EXPLANATION') return 'afterExplanation';
  return feedback.path === 'AFTER_HINT' ? 'afterHint' : 'independent';
}

const errorMessage = (error: unknown) =>
  error instanceof ApiError && error.serverMessage ? error.serverMessage : '연결이 잠시 끊겼어요. 다시 시도해 주세요.';

/**
 * 풀이 (스펙 §7 5·6단계). 문제·판정·다음 행동은 서버가 정한다.
 * 오답이면 서버가 힌트(재도전) 또는 개념 설명을 고르고, 설명 뒤 확인 문제는 서버가 큐 끝에 넣는다.
 */
export function Review({ practiceId, sessionId }: { practiceId: number | null; sessionId: number | null }) {
  const insets = useSafeAreaInsets();
  const queryClient = useQueryClient();
  const scroll = useRef<ScrollView>(null);
  const [presentation, setPresentation] = useState<Presentation | null>(null);
  const [phase, setPhase] = useState<Phase>({ kind: 'loading' });
  const [answer, setAnswer] = useState<Answer>(emptyAnswer);
  const [selfAssessment, setSelfAssessment] = useState<SelfAssessment | null>(null);
  const [hint, setHint] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [results, setResults] = useState<Map<number, ItemResult>>(() => new Map());
  // 낸 답이 판정까지 끝났는데 피드백 요청만 실패했으면, 다시 시도할 때 답을 또 내지 않는다.
  const pendingAttempt = useRef<Attempt | null>(null);
  // 응답 시간: 문제(재도전이면 힌트)를 보여준 때부터 잰다.
  const shownAt = useRef(0);
  const firstInputAt = useRef<number | null>(null);

  const show = useCallback((next: Next) => {
    if (next.done || !next.presentation) {
      setPhase({ kind: 'done' });
      // 풀이가 기억 상태를 바꿨으므로 게이지·요약·세션 상태를 다시 읽는다.
      for (const key of ['memory-gauge', 'first-study-summary', 'learning-sessions', 'daily', 'streak']) {
        queryClient.invalidateQueries({ queryKey: [key] });
      }
      return;
    }
    setPresentation(next.presentation);
    setAnswer(emptyAnswer);
    setSelfAssessment(null);
    setHint(null);
    pendingAttempt.current = null;
    shownAt.current = Date.now();
    firstInputAt.current = null;
    setPhase({ kind: 'answering', retry: false });
    scroll.current?.scrollTo({ y: 0, animated: false });
  }, [queryClient]);

  const fail = useCallback((e: unknown) => setError(errorMessage(e)), []);

  useEffect(() => {
    if (practiceId !== null) fetchNext(practiceId).then(show, fail);
  }, [practiceId, show, fail]);

  function loadNext() {
    if (practiceId === null) return;
    setPhase({ kind: 'loading' });
    setError(null);
    fetchNext(practiceId).then(show, fail);
  }

  if (practiceId === null) {
    return (
      <View style={[styles.screen, styles.centered]}>
        <ThemedText variant="title">풀 문제가 없어요</ThemedText>
        <ThemedText variant="subhead" tone="inkMuted" style={styles.center}>
          학습 내용을 확인하고 문제를 준비하면 여기서 풀 수 있어요.
        </ThemedText>
        <Button variant="secondary" title="홈으로" onPress={() => router.navigate('/')} />
      </View>
    );
  }

  if (phase.kind === 'done') {
    return (
      <View style={styles.screen}>
        <Stack.Screen options={{ title: '학습 완료' }} />
        <ReviewComplete results={[...results.values()]} sessionId={sessionId} />
      </View>
    );
  }

  if (!presentation) {
    return (
      <View style={[styles.screen, styles.centered]}>
        {error ? (
          <>
            <Notice tone="danger">{error}</Notice>
            <Button variant="secondary" title="다시 시도" onPress={loadNext} />
          </>
        ) : (
          <ActivityIndicator color={colors.primary} />
        )}
      </View>
    );
  }

  const recheck = presentation.sameDayRecheck;
  const answering = phase.kind === 'answering' || phase.kind === 'checking';
  const retry = (phase.kind === 'answering' || phase.kind === 'checking') && phase.retry;
  const asksSelfAssessment = answering && !retry;
  const result = phase.kind === 'result' ? phase : null;
  const canSubmit = isAnswered(presentation.type, answer) && (!asksSelfAssessment || selfAssessment !== null);

  function changeAnswer(next: Answer) {
    if (firstInputAt.current === null) firstInputAt.current = Date.now();
    setAnswer(next);
  }

  async function check() {
    if (!presentation) return;
    setPhase({ kind: 'checking', retry });
    setError(null);
    try {
      const now = Date.now();
      const attempt =
        pendingAttempt.current ??
        (await submitAttempt(presentation.presentationId, {
          choiceIndex: presentation.type === 'MULTIPLE_CHOICE' ? answer.choiceIndex : null,
          answer: presentation.type === 'MULTIPLE_CHOICE' ? null : answer.text.trim(),
          selfAssessment: retry ? null : selfAssessment,
          responseTimeMs: now - shownAt.current,
          firstInputMs: firstInputAt.current === null ? null : firstInputAt.current - shownAt.current,
        }));
      pendingAttempt.current = attempt;
      const feedback = await decideFeedback(presentation.presentationId);
      pendingAttempt.current = null;
      const outcome = outcomeOf(attempt, feedback);
      if (feedback.hint) setHint(feedback.hint);
      setResults((prev) => {
        const map = new Map(prev);
        const earlier = map.get(presentation.questionId);
        const path = pathOf(outcome, feedback, recheck ? earlier : undefined);
        const explained = (earlier?.explained ?? false) || feedback.action === 'EXPLAIN_CONCEPT';
        // 확인 문제가 보류되면 원래 문제의 경로를 그대로 둔다.
        if (!(recheck && path === 'held' && earlier)) {
          map.set(presentation.questionId, {
            questionId: presentation.questionId,
            stem: earlier?.stem ?? presentation.stem,
            path,
            explained,
          });
        }
        return map;
      });
      setPhase({ kind: 'result', attempt, feedback, outcome });
    } catch (e) {
      setError(errorMessage(e));
      setPhase({ kind: 'answering', retry });
    }
  }

  function retryWithHint() {
    setAnswer(emptyAnswer);
    shownAt.current = Date.now();
    firstInputAt.current = null;
    setPhase({ kind: 'answering', retry: true });
    scroll.current?.scrollTo({ y: 0, animated: true });
  }

  const graded = result && (result.outcome === 'correct' || result.outcome === 'wrong') ? result.outcome : null;
  const moreQueued = result?.feedback.recheckQueued || result?.feedback.action === 'RELEARN_TODAY';
  const last = presentation.position + 1 >= presentation.total && !moreQueued;

  return (
    <View style={styles.screen}>
      <Stack.Screen options={{ title: `${presentation.position + 1} / ${presentation.total}` }} />
      <View
        accessible
        accessibilityRole="progressbar"
        accessibilityLabel="풀이 진행"
        accessibilityValue={{ min: 0, max: presentation.total, now: presentation.position }}
        style={styles.progressTrack}>
        <View
          style={[styles.progressFill, { width: `${Math.round((presentation.position / presentation.total) * 100)}%` }]}
        />
      </View>

      <ScrollView ref={scroll} keyboardShouldPersistTaps="handled" contentContainerStyle={styles.content}>
        <View style={styles.chips}>
          {recheck && <Chip variant="status" label="확인 문제" />}
          <Chip label={questionTypeLabel[presentation.type]} />
        </View>

        <ThemedText variant="question" style={styles.prompt}>
          {presentation.stem}
        </ThemedText>

        {hint && (retry || result?.feedback.action === 'GIVE_HINT') && (
          <Card variant="lavender" style={styles.box}>
            <ThemedText variant="headline">힌트</ThemedText>
            <ThemedText variant="subhead" tone="inkSecondary">
              {hint}
            </ThemedText>
          </Card>
        )}

        <AnswerInput
          presentation={presentation}
          answer={answer}
          onChange={changeAnswer}
          locked={!answering || phase.kind === 'checking'}
          result={graded}
        />

        {asksSelfAssessment && (
          <View accessibilityRole="radiogroup" accessibilityLabel="얼마나 확신하나요" style={styles.selfAssessment}>
            <ThemedText variant="subhead" tone="inkSecondary">
              얼마나 확신하나요?
            </ThemedText>
            {SELF_ASSESSMENT.map((s) => (
              <OptionRow
                key={s.key}
                selected={selfAssessment === s.key}
                onPress={() => setSelfAssessment(s.key)}
                style={styles.selfAssessmentRow}>
                <ThemedText variant="subhead">{s.label}</ThemedText>
              </OptionRow>
            ))}
          </View>
        )}

        {error && phase.kind !== 'result' && <Notice tone="danger">{error}</Notice>}

        {result && <ResultView result={result} recheck={recheck} retried={result.attempt.kind === 'ASSISTED_RETRY'} />}
      </ScrollView>

      <View style={[styles.footer, { paddingBottom: insets.bottom + spacing.xl }]}>
        {answering && (
          <Button title="제출" disabled={!canSubmit} loading={phase.kind === 'checking'} onPress={check} />
        )}
        {phase.kind === 'loading' && (
          <Button title={error ? '다시 시도' : '다음 문제'} loading={!error} onPress={loadNext} />
        )}
        {result?.feedback.action === 'GIVE_HINT' && <Button title="힌트 보고 다시 풀기" onPress={retryWithHint} />}
        {result && result.feedback.action !== 'GIVE_HINT' && (
          <Button title={last ? '결과 보기' : '다음 문제'} onPress={loadNext} />
        )}
      </View>
    </View>
  );
}

/** 판정과 서버가 정한 다음 행동을 보여준다. */
function ResultView({
  result: { attempt, feedback, outcome },
  recheck,
  retried,
}: {
  result: Extract<Phase, { kind: 'result' }>;
  recheck: boolean;
  retried: boolean;
}) {
  return (
    <>
      {outcome === 'correct' && (
        <View style={[styles.box, styles.correct]}>
          <ThemedText variant="headline" tone="successInk">
            {recheck ? '확인 문제도 맞혔어요' : feedback.path === 'AFTER_HINT' ? '힌트를 보고 맞혔어요' : '맞았어요'}
          </ThemedText>
          {attempt.holdReason === 'GUESS_UNCONFIRMED' && (
            <ThemedText variant="subhead" tone="successInk">
              아주 빨리 골라서 추측일 수도 있어요. 이번 답은 기억 상태에 반영하지 않았어요.
            </ThemedText>
          )}
          {feedback.action === 'RELEARN_TODAY' && (
            <ThemedText variant="caption" tone="inkSecondary">
              도움을 받고 맞혔으니 오늘 한 번 더 확인할게요.
            </ThemedText>
          )}
        </View>
      )}

      {outcome === 'wrong' && (
        <View style={[styles.box, styles.wrong]}>
          <ThemedText variant="headline" tone="dangerInk">
            {retried ? '이번에도 아쉬워요' : '아쉬워요'}
          </ThemedText>
          <ThemedText variant="subhead" tone="dangerInk">
            {feedback.action === 'GIVE_HINT'
              ? '힌트를 보고 한 번 더 풀어 볼까요?'
              : feedback.action === 'EXPLAIN_CONCEPT'
                ? '개념을 다시 짚어 볼게요.'
                : feedback.action === 'RELEARN_TODAY'
                  ? '남은 문제를 푼 뒤에 오늘 한 번 더 물어볼게요.'
                  : '다음 학습에서 다시 볼게요.'}
          </ThemedText>
        </View>
      )}

      {outcome === 'uncertain' && (
        <Card style={styles.box}>
          <ThemedText variant="headline">판정을 보류했어요</ThemedText>
          <ThemedText variant="subhead" tone="inkSecondary">
            답이 기준을 충족했는지 확실하게 판정하지 못했어요. 이번 답은 기억 상태에 반영하지 않아요.
          </ThemedText>
        </Card>
      )}

      {outcome === 'ambiguous' && (
        <Card style={styles.box}>
          <ThemedText variant="headline">
            {attempt.holdReason === 'MISREAD' ? '질문을 다르게 읽은 것 같아요' : '이 답만으로는 판단하기 어려워요'}
          </ThemedText>
          <ThemedText variant="subhead" tone="inkSecondary">
            {feedback.recheckQueued
              ? '문제가 애매했을 수 있어요. 기억 상태는 그대로 두고 다른 문제로 다시 물어볼게요.'
              : '문제가 애매했을 수 있어요. 기억 상태는 그대로 둘게요.'}
          </ThemedText>
        </Card>
      )}

      {feedback.action === 'EXPLAIN_CONCEPT' && feedback.explanation && (
        <Card style={styles.box}>
          <ThemedText variant="headline">개념 설명</ThemedText>
          <ThemedText variant="subhead" tone="inkSecondary">
            {feedback.explanation}
          </ThemedText>
          {feedback.evidenceTurns.length > 0 && (
            <ThemedText variant="caption" tone="inkMuted">
              근거: 대화 {feedback.evidenceTurns.map((t) => `#${t}`).join(', ')}
            </ThemedText>
          )}
          {feedback.recheckQueued && (
            <ThemedText variant="caption" tone="primaryInk">
              남은 문제를 푼 뒤에 확인 문제로 다시 물어볼게요.
            </ThemedText>
          )}
        </Card>
      )}

      {feedback.prerequisite && (
        <Notice>
          먼저 「{feedback.prerequisite.concept}」을(를) 다시 보면 좋아요. {feedback.prerequisite.reason}
        </Notice>
      )}
    </>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.canvas },
  centered: { alignItems: 'center', justifyContent: 'center', gap: spacing.md, padding: spacing.xl },
  progressTrack: {
    height: components.gauge.height,
    marginHorizontal: spacing.xl,
    borderRadius: components.gauge.rounded,
    backgroundColor: components.gauge.backgroundColor,
    overflow: 'hidden',
  },
  progressFill: { height: '100%', backgroundColor: components.gaugeFill.backgroundColor },
  content: { flexGrow: 1, padding: spacing.xl, gap: spacing.xl },
  chips: { flexDirection: 'row', justifyContent: 'center', gap: spacing.sm },
  center: { textAlign: 'center' },
  prompt: { textAlign: 'center', paddingHorizontal: spacing.sm },
  box: {
    gap: spacing.sm,
    borderRadius: components.feedbackCorrect.rounded,
    borderCurve: 'continuous',
    padding: components.feedbackCorrect.padding,
  },
  correct: { backgroundColor: components.feedbackCorrect.backgroundColor },
  wrong: { backgroundColor: components.feedbackWrong.backgroundColor },
  selfAssessment: { gap: spacing.sm },
  selfAssessmentRow: { paddingVertical: spacing.md },
  footer: { paddingHorizontal: spacing.xl, paddingTop: spacing.md, gap: spacing.sm },
});
