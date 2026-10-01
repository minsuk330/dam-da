import { Stack } from 'expo-router';
import { useRef, useState } from 'react';
import { ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { Chip } from '@/components/chip';
import { OptionRow } from '@/components/option-row';
import { ThemedText } from '@/components/themed-text';
import { colors, components, spacing } from '@/theme';

import { AnswerInput } from './answer-input';
import { ReviewComplete, type ItemResult, type Path } from './complete';
import {
  emptyAnswer,
  gradeSample,
  isAnswered,
  sampleQuestions,
  type Answer,
  type Question,
  type QuestionType,
} from './sample-questions';

/** 답할 때 고르는 자기평가(스펙 §6.4.5). FSRS 등급은 판정과 이 값으로 코드가 정한다. */
const CONFIDENCE = [
  { key: 'easy', label: '쉽게 떠올렸어요' },
  { key: 'hard', label: '힘들게 떠올렸거나 확신이 약해요' },
  { key: 'guess', label: '떠올리지 못하고 추측했어요' },
] as const;
type Confidence = (typeof CONFIDENCE)[number]['key'];

const typeLabel: Record<QuestionType, string> = {
  MULTIPLE_CHOICE: '객관식',
  SHORT_ANSWER: '단답',
  ESSAY: '서술',
  ERROR_FINDING: '오류 찾기',
};

/** 개념 설명 뒤 확인 문제를 몇 문제 뒤에 낼지(스펙 §6.4.8: 바로 다시 묻지 않는다). */
const FOLLOW_UP_GAP = 2;

/**
 * - answering: 첫 무도움 시도. 확신도를 함께 받는다(평가 대상).
 * - wrong: 첫 시도 오답. 정답은 아직 숨긴다.
 * - retrying: 힌트를 본 재도전. 평가하지 않으므로 확신도를 받지 않는다.
 * - correct / explained: 결과를 보여주고 다음으로.
 */
type Phase = 'answering' | 'wrong' | 'retrying' | 'correct' | 'explained';

type Entry = { question: Question; origin: Question; followUp: boolean };

/** 풀이 (스펙 §7 5·6단계): 4유형 입력 + 확신도, 오답 → 힌트 → 재도전 → 개념 설명 → 확인 문제. 문제·채점은 예시다. */
export function Review() {
  const insets = useSafeAreaInsets();
  const scroll = useRef<ScrollView>(null);
  const [queue, setQueue] = useState<Entry[]>(() =>
    sampleQuestions.map((q) => ({ question: q, origin: q, followUp: false })),
  );
  const [position, setPosition] = useState(0);
  const [phase, setPhase] = useState<Phase>('answering');
  const [answer, setAnswer] = useState<Answer>(() => emptyAnswer(sampleQuestions[0]));
  const [confidence, setConfidence] = useState<Confidence | null>(null);
  const [results, setResults] = useState<Record<string, Path>>({});

  if (position >= queue.length) {
    const finished: ItemResult[] = sampleQuestions
      .filter((q) => results[q.id])
      .map((q) => ({ question: q, path: results[q.id] }));
    return (
      <View style={styles.screen}>
        <Stack.Screen options={{ title: '학습 완료' }} />
        <ReviewComplete results={finished} />
      </View>
    );
  }

  const { question, origin, followUp } = queue[position];
  const asksConfidence = phase === 'answering';
  const graded = phase === 'wrong' || phase === 'correct' || phase === 'explained';
  const last = position === queue.length - 1;
  const textType = question.type === 'SHORT_ANSWER' || question.type === 'ESSAY';
  const followUpQueued = queue.slice(position + 1).some((e) => e.followUp && e.origin.id === origin.id);

  const record = (path: Path) => setResults((r) => ({ ...r, [origin.id]: path }));

  function explain() {
    setPhase('explained');
    if (followUp) {
      record('repeatedWrong');
    } else if (question.followUp) {
      const at = Math.min(position + 1 + FOLLOW_UP_GAP, queue.length);
      const entry: Entry = { question: question.followUp, origin: question, followUp: true };
      setQueue((q) => [...q.slice(0, at), entry, ...q.slice(at)]);
    } else {
      record('afterExplanation');
    }
  }

  function submit() {
    const correct = gradeSample(question, answer);
    if (followUp) {
      if (correct) {
        record('afterExplanation');
        setPhase('correct');
      } else {
        explain();
      }
    } else if (phase === 'answering') {
      if (correct) {
        record('independent');
        setPhase('correct');
      } else {
        setPhase('wrong');
      }
    } else if (correct) {
      record('afterHint');
      setPhase('correct');
    } else {
      explain();
    }
  }

  function retryWithHint() {
    setAnswer(emptyAnswer(question));
    setPhase('retrying');
  }

  function next() {
    const nextPosition = position + 1;
    setPosition(nextPosition);
    if (nextPosition < queue.length) setAnswer(emptyAnswer(queue[nextPosition].question));
    setPhase('answering');
    setConfidence(null);
    scroll.current?.scrollTo({ y: 0, animated: false });
  }

  const canSubmit = isAnswered(answer) && (!asksConfidence || confidence !== null);

  return (
    <View style={styles.screen}>
      <Stack.Screen options={{ title: `${position + 1} / ${queue.length}` }} />
      <View
        accessible
        accessibilityRole="progressbar"
        accessibilityLabel="풀이 진행"
        accessibilityValue={{ min: 0, max: queue.length, now: position }}
        style={styles.progressTrack}>
        <View style={[styles.progressFill, { width: `${Math.round((position / queue.length) * 100)}%` }]} />
      </View>

      <ScrollView ref={scroll} keyboardShouldPersistTaps="handled" contentContainerStyle={styles.content}>
        <View style={styles.meta}>
          <View style={styles.chips}>
            <Chip variant={followUp ? 'status' : 'soft'} label={followUp ? '확인 문제' : question.kind} />
            <Chip label={typeLabel[question.type]} />
          </View>
          <ThemedText variant="caption" tone="inkMuted">
            {question.unitTitle} · 예시 문제
          </ThemedText>
        </View>

        <ThemedText variant="question" style={styles.prompt}>
          {question.prompt}
        </ThemedText>

        {phase === 'retrying' && (
          <Card variant="lavender" style={styles.box}>
            <ThemedText variant="headline">힌트</ThemedText>
            <ThemedText variant="subhead" tone="inkSecondary">
              {question.hint}
            </ThemedText>
          </Card>
        )}

        <AnswerInput
          question={question}
          answer={answer}
          onChange={setAnswer}
          graded={graded}
          revealAnswer={phase !== 'wrong'}
        />

        {asksConfidence && (
          <View accessibilityRole="radiogroup" accessibilityLabel="얼마나 확신하나요" style={styles.confidence}>
            <ThemedText variant="subhead" tone="inkSecondary">
              얼마나 확신하나요?
            </ThemedText>
            {CONFIDENCE.map((c) => (
              <OptionRow key={c.key} selected={confidence === c.key} onPress={() => setConfidence(c.key)} style={styles.confidenceRow}>
                <ThemedText variant="subhead">{c.label}</ThemedText>
              </OptionRow>
            ))}
          </View>
        )}

        {phase === 'correct' && (
          <View style={[styles.box, styles.correct]}>
            <ThemedText variant="headline" tone="successInk">
              {followUp ? '확인 문제도 맞혔어요' : results[origin.id] === 'afterHint' ? '힌트를 보고 맞혔어요' : '맞았어요'}
            </ThemedText>
            <ThemedText variant="subhead" tone="successInk">
              {question.explanation}
            </ThemedText>
            <ThemedText variant="caption" tone="inkSecondary">
              근거: 대화 #{question.evidenceTurn}
            </ThemedText>
          </View>
        )}

        {phase === 'wrong' && (
          <View style={[styles.box, styles.wrong]}>
            <ThemedText variant="headline" tone="dangerInk">
              아쉬워요
            </ThemedText>
            <ThemedText variant="subhead" tone="dangerInk">
              힌트를 보고 한 번 더 풀어 볼까요?
            </ThemedText>
          </View>
        )}

        {phase === 'explained' && (
          <Card style={styles.box}>
            <ThemedText variant="headline">개념 설명</ThemedText>
            {question.userBelief && (
              <View style={styles.compare}>
                <ThemedText variant="caption" tone="dangerInk">
                  대화에서 생각한 것: {question.userBelief}
                </ThemedText>
                <ThemedText variant="caption" tone="successInk">
                  정확히는: {question.answerText}
                </ThemedText>
              </View>
            )}
            {!question.userBelief && (
              <ThemedText variant="subhead" tone="successInk">
                정답: {question.answerText}
              </ThemedText>
            )}
            <ThemedText variant="subhead" tone="inkSecondary">
              {question.explanation}
            </ThemedText>
            <ThemedText variant="caption" tone="inkMuted">
              근거: 대화 #{question.evidenceTurn}
            </ThemedText>
            {followUpQueued && (
              <ThemedText variant="caption" tone="primaryInk">
                몇 문제 뒤에 확인 문제로 다시 물어볼게요.
              </ThemedText>
            )}
            {followUp && (
              <ThemedText variant="caption" tone="primaryInk">
                오늘 한 번 더 복습할게요.
              </ThemedText>
            )}
          </Card>
        )}

        {graded && textType && (
          <ThemedText variant="caption" tone="inkMuted" style={styles.center}>
            예시 채점이에요. 실제로는 Jev가 정답 기준을 충족했는지 판정해요.
          </ThemedText>
        )}
      </ScrollView>

      <View style={[styles.footer, { paddingBottom: insets.bottom + spacing.xl }]}>
        {(phase === 'answering' || phase === 'retrying') && <Button title="제출" disabled={!canSubmit} onPress={submit} />}
        {phase === 'wrong' && (
          <>
            <Button title="힌트 보고 다시 풀기" onPress={retryWithHint} />
            <Button variant="secondary" title="설명 바로 보기" onPress={explain} />
          </>
        )}
        {(phase === 'correct' || phase === 'explained') && <Button title={last ? '결과 보기' : '다음 문제'} onPress={next} />}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.canvas },
  progressTrack: {
    height: components.gauge.height,
    marginHorizontal: spacing.xl,
    borderRadius: components.gauge.rounded,
    backgroundColor: components.gauge.backgroundColor,
    overflow: 'hidden',
  },
  progressFill: { height: '100%', backgroundColor: components.gaugeFill.backgroundColor },
  content: { flexGrow: 1, padding: spacing.xl, gap: spacing.xl },
  meta: { alignItems: 'center', gap: spacing.sm },
  chips: { flexDirection: 'row', gap: spacing.sm },
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
  compare: { gap: spacing.xs },
  confidence: { gap: spacing.sm },
  confidenceRow: { paddingVertical: spacing.md },
  footer: { paddingHorizontal: spacing.xl, paddingTop: spacing.md, gap: spacing.sm },
});
