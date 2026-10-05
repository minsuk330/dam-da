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
import { ChoiceChip } from '@/components/choice-chip';
import { Icon } from '@/components/icon';
import { Notice } from '@/components/notice';
import { Skeleton } from '@/components/skeleton';
import { ThemedText } from '@/components/themed-text';
import { questionTypeLabel } from '@/labels';
import { colors, components, spacing } from '@/theme';

import { AnswerInput, emptyAnswer, isAnswered, type Answer } from './answer-input';
import { ReviewComplete, type ItemResult, type Path } from './complete';

/** 첫 무도움 시도에 함께 받는 자기평가(스펙 §6.4.5). FSRS 등급은 판정과 이 값으로 서버가 정한다. */
const SELF_ASSESSMENT: { key: SelfAssessment; label: string; description: string }[] = [
  { key: 'RECALLED_EASILY', label: '바로 떠올림', description: '쉽게 떠올렸어요' },
  { key: 'RECALLED_WITH_EFFORT', label: '겨우 떠올림', description: '힘들게 떠올렸거나 확신이 약해요' },
  { key: 'GUESSED', label: '추측함', description: '떠올리지 못하고 추측했어요' },
];

/** 이보다 긴 문제는 가운데 정렬이 읽기 어려워 왼쪽 정렬로 둔다(사례 판단·서술 문제). */
const LONG_STEM = 60;

/** 판정 결과. 보류(`uncertain`·`ambiguous`)면 맞음·틀림을 보여주지 않는다. */
type Outcome = 'correct' | 'wrong' | 'uncertain' | 'ambiguous';

/**
 * - loading: 다음 문제를 불러오는 중
 * - answering: 답하는 중. `retry`면 힌트 뒤 재도전이라 평가하지 않으므로 자기평가를 받지 않는다.
 * - checking: 답을 내고 판정을 기다리는 중
 * - result: 판정을 보여준다. 서버가 정한 다음 행동(힌트·개념 설명·다음 문제 등)은 `feedback`이 오면 이어서 보여준다.
 *   힌트·설명 생성은 몇 초 걸려서, 판정(제출 응답)을 먼저 보여주고 기다리게 한다.
 */
type Phase =
  | { kind: 'loading' }
  | { kind: 'answering'; retry: boolean }
  | { kind: 'checking'; retry: boolean }
  | { kind: 'result'; attempt: Attempt; feedback: Feedback | null; outcome: Outcome }
  | { kind: 'done' };

/** 서버가 정한 시도 결과. 피드백이 받는 결과와 같아서 피드백을 기다리지 않고 보여줄 수 있다. */
const OUTCOME: Record<Attempt['outcome'], Outcome> = {
  CORRECT: 'correct',
  WRONG: 'wrong',
  UNCERTAIN: 'uncertain',
  QUESTION_AMBIGUOUS: 'ambiguous',
};

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
    let attempt: Attempt;
    try {
      const now = Date.now();
      attempt = await submitAttempt(presentation.presentationId, {
        choiceIndex: presentation.type === 'MULTIPLE_CHOICE' ? answer.choiceIndex : null,
        answer: presentation.type === 'MULTIPLE_CHOICE' ? null : answer.text.trim(),
        selfAssessment: retry ? null : selfAssessment,
        responseTimeMs: now - shownAt.current,
        firstInputMs: firstInputAt.current === null ? null : firstInputAt.current - shownAt.current,
      });
    } catch (e) {
      setError(errorMessage(e));
      setPhase({ kind: 'answering', retry });
      return;
    }
    // 판정은 바로 보여주고 다음 행동은 이어서 받는다.
    setPhase({ kind: 'result', attempt, feedback: null, outcome: OUTCOME[attempt.outcome] });
    await loadFeedback(attempt);
  }

  /** 다음 행동을 받는다. 실패하면 판정은 둔 채 다시 시도하게 한다(답을 또 내지 않는다). */
  async function loadFeedback(attempt: Attempt) {
    if (!presentation) return;
    setError(null);
    try {
      const feedback = await decideFeedback(presentation.presentationId);
      const outcome = OUTCOME[attempt.outcome];
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
  const feedback = result?.feedback ?? null;
  const moreQueued = feedback?.recheckQueued || feedback?.action === 'RELEARN_TODAY';
  const last = presentation.position + 1 >= presentation.total && !moreQueued;
  // 진행 막대는 답한 문제 수다. 결과를 보고 있으면 지금 문제까지 센다.
  const answered = presentation.position + (result ? 1 : 0);
  const needsSelfAssessment =
    asksSelfAssessment && selfAssessment === null && isAnswered(presentation.type, answer) && phase.kind !== 'checking';

  return (
    <View style={styles.screen}>
      <Stack.Screen options={{ title: `${presentation.position + 1} / ${presentation.total}` }} />
      <View
        accessible
        accessibilityRole="progressbar"
        accessibilityLabel="풀이 진행"
        accessibilityValue={{ min: 0, max: presentation.total, now: answered }}
        style={styles.progressTrack}>
        <View style={[styles.progressFill, { width: `${Math.round((answered / presentation.total) * 100)}%` }]} />
      </View>

      <ScrollView ref={scroll} keyboardShouldPersistTaps="handled" contentContainerStyle={styles.content}>
        <View style={[styles.chips, presentation.stem.length > LONG_STEM && styles.chipsLong]}>
          {recheck && <Chip variant="status" label="확인 문제" />}
          <Chip label={questionTypeLabel[presentation.type]} />
          {/* 생성형 AI 결과물 표시(앱인토스 서비스 오픈 정책 2-4, #149). */}
          <Chip label="AI 생성" />
        </View>

        <ThemedText variant="question" style={[styles.prompt, presentation.stem.length > LONG_STEM && styles.promptLong]}>
          {presentation.stem}
        </ThemedText>

        {hint && (retry || feedback?.action === 'GIVE_HINT') && (
          <Card variant="lavender" style={styles.box}>
            <ThemedText variant="headline">힌트</ThemedText>
            <AiNote>AI가 만든 힌트예요</AiNote>
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
            <View style={styles.selfAssessmentRow}>
              {SELF_ASSESSMENT.map((s) => (
                <ChoiceChip
                  key={s.key}
                  label={s.label}
                  accessibilityLabel={s.description}
                  selected={selfAssessment === s.key}
                  onPress={() => setSelfAssessment(s.key)}
                  style={styles.selfAssessmentChip}
                />
              ))}
            </View>
            {selfAssessment && (
              <ThemedText variant="caption" tone="inkMuted">
                {SELF_ASSESSMENT.find((s) => s.key === selfAssessment)?.description}
              </ThemedText>
            )}
          </View>
        )}

        {result && <ResultView result={result} recheck={recheck} retried={result.attempt.kind === 'ASSISTED_RETRY'} />}
      </ScrollView>

      <View style={[styles.footer, { paddingBottom: insets.bottom + spacing.xl }]}>
        {/* 제출·불러오기 실패는 누른 버튼 바로 위에 둔다. 스크롤 아래에 있으면 긴 문제에서 보이지 않는다. */}
        {error && (phase.kind !== 'result' || !feedback) && <Notice tone="danger">{error}</Notice>}
        {needsSelfAssessment && (
          <ThemedText variant="caption" tone="inkSecondary" style={styles.center}>
            얼마나 확신하는지 골라야 제출할 수 있어요
          </ThemedText>
        )}
        {answering && (
          <Button title="제출" disabled={!canSubmit} loading={phase.kind === 'checking'} onPress={check} />
        )}
        {phase.kind === 'loading' && (
          <Button title={error ? '다시 시도' : '다음 문제'} loading={!error} onPress={loadNext} />
        )}
        {result && !feedback && (
          <Button
            title={error ? '다시 시도' : '다음 문제'}
            loading={!error}
            onPress={() => loadFeedback(result.attempt)}
          />
        )}
        {feedback?.action === 'GIVE_HINT' && <Button title="힌트 보고 다시 풀기" onPress={retryWithHint} />}
        {feedback && feedback.action !== 'GIVE_HINT' && (
          <Button title={last ? '결과 보기' : '다음 문제'} onPress={loadNext} />
        )}
      </View>
    </View>
  );
}

/** 판정과 서버가 정한 다음 행동을 보여준다. 다음 행동(`feedback`)이 아직 없으면 그 자리를 비워 둔다. */
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
          <View style={styles.resultTitle}>
            <Icon name="check-circle" color={colors.successInk} />
            <ThemedText variant="headline" tone="successInk">
              {recheck ? '확인 문제도 맞혔어요' : retried ? '힌트를 보고 맞혔어요' : '맞았어요'}
            </ThemedText>
          </View>
          {attempt.holdReason === 'GUESS_UNCONFIRMED' && (
            <ThemedText variant="subhead" tone="successInk">
              아주 빨리 골라서 추측일 수도 있어요. 이번 답은 기억 상태에 반영하지 않았어요.
            </ThemedText>
          )}
          {feedback?.action === 'RELEARN_TODAY' && (
            <ThemedText variant="caption" tone="inkSecondary">
              도움을 받고 맞혔으니 오늘 한 번 더 확인할게요.
            </ThemedText>
          )}
        </View>
      )}

      {outcome === 'wrong' && (
        <View style={[styles.box, styles.wrong]}>
          <View style={styles.resultTitle}>
            <Icon name="x-circle" color={colors.dangerInk} />
            <ThemedText variant="headline" tone="dangerInk">
              {retried ? '이번에도 아쉬워요' : '아쉬워요'}
            </ThemedText>
          </View>
          {feedback ? (
            <ThemedText variant="subhead" tone="dangerInk">
              {feedback.action === 'GIVE_HINT'
                ? '힌트를 보고 한 번 더 풀어 볼까요?'
                : feedback.action === 'EXPLAIN_CONCEPT'
                  ? '개념을 다시 짚어 볼게요.'
                  : feedback.action === 'RELEARN_TODAY'
                    ? '남은 문제를 푼 뒤에 오늘 한 번 더 물어볼게요.'
                    : '다음 학습에서 다시 볼게요.'}
            </ThemedText>
          ) : (
            <Skeleton width="70%" />
          )}
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
            문제가 애매했을 수 있어요. 기억 상태는 그대로 둘게요.
          </ThemedText>
          {feedback?.recheckQueued && (
            <ThemedText variant="caption" tone="primaryInk">
              다른 문제로 다시 물어볼게요.
            </ThemedText>
          )}
        </Card>
      )}

      {feedback?.action === 'EXPLAIN_CONCEPT' && feedback.explanation && (
        <Card style={styles.box}>
          <ThemedText variant="headline">개념 설명</ThemedText>
          <AiNote>AI가 만든 설명이에요</AiNote>
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

      {feedback?.prerequisite && (
        <Notice>
          먼저 「{feedback.prerequisite.concept}」을(를) 다시 보면 좋아요. {feedback.prerequisite.reason}
        </Notice>
      )}

      <AiNote>채점과 다음 학습 안내는 생성형 AI가 해요. 틀릴 수 있어요.</AiNote>
    </>
  );
}

/** 생성형 AI 결과물 표시(앱인토스 서비스 오픈 정책 2-4, #149). */
function AiNote({ children }: { children: string }) {
  return (
    <ThemedText variant="caption" tone="inkMuted">
      {children}
    </ThemedText>
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
  chipsLong: { justifyContent: 'flex-start' },
  center: { textAlign: 'center' },
  prompt: { textAlign: 'center', paddingHorizontal: spacing.sm },
  promptLong: { textAlign: 'left', paddingHorizontal: 0 },
  resultTitle: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm },
  box: {
    gap: spacing.sm,
    borderRadius: components.feedbackCorrect.rounded,
    borderCurve: 'continuous',
    padding: components.feedbackCorrect.padding,
  },
  correct: { backgroundColor: components.feedbackCorrect.backgroundColor },
  wrong: { backgroundColor: components.feedbackWrong.backgroundColor },
  selfAssessment: { gap: spacing.sm },
  selfAssessmentRow: { flexDirection: 'row', gap: spacing.sm },
  selfAssessmentChip: { flex: 1 },
  footer: { paddingHorizontal: spacing.xl, paddingTop: spacing.md, gap: spacing.sm },
});
