import { router, Stack } from 'expo-router';
import { useState } from 'react';
import { ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Button } from '@/components/button';
import { Chip } from '@/components/chip';
import { Icon } from '@/components/icon';
import { ThemedText } from '@/components/themed-text';
import { colors, components, spacing } from '@/theme';

import { AnswerOption, type OptionState } from './answer-option';
import { sampleQuestions } from './sample-questions';

export function Review() {
  const insets = useSafeAreaInsets();
  const [index, setIndex] = useState(0);
  const [selected, setSelected] = useState<number | null>(null);
  const [checked, setChecked] = useState(false);
  const [correctCount, setCorrectCount] = useState(0);
  const finished = index >= sampleQuestions.length;

  if (finished) {
    return (
      <View style={[styles.screen, styles.done, { paddingBottom: insets.bottom + spacing.xl }]}>
        <Stack.Screen options={{ title: '오늘의 복습' }} />
        <View style={styles.doneBody}>
          <View style={styles.doneIcon}>
            <Icon name="check" size={32} color={colors.onPrimary} />
          </View>
          <ThemedText variant="display" style={styles.center}>
            오늘 복습을 마쳤어요
          </ThemedText>
          <ThemedText variant="body" tone="inkSecondary" style={styles.center}>
            {sampleQuestions.length}문제 중 {correctCount}문제를 맞혔어요.
          </ThemedText>
        </View>
        <Button title="홈으로" onPress={() => router.back()} />
      </View>
    );
  }

  const question = sampleQuestions[index];
  const isCorrect = selected === question.answerIndex;
  const last = index === sampleQuestions.length - 1;

  const optionState = (i: number): OptionState => {
    if (!checked) return selected === i ? 'selected' : 'idle';
    if (i === question.answerIndex) return 'correct';
    if (i === selected) return 'wrong';
    return 'dimmed';
  };

  const onPrimary = () => {
    if (!checked) {
      setChecked(true);
      if (isCorrect) setCorrectCount((n) => n + 1);
      return;
    }
    setIndex((i) => i + 1);
    setSelected(null);
    setChecked(false);
  };

  return (
    <View style={styles.screen}>
      <Stack.Screen options={{ title: `${index + 1} / ${sampleQuestions.length}` }} />
      <ScrollView contentContainerStyle={styles.content}>
        <View style={styles.meta}>
          <Chip variant={question.kind === '헷갈렸던 점' ? 'warning' : 'soft'} label={question.kind} style={styles.selfCenter} />
          <ThemedText variant="caption" tone="inkMuted">
            {question.unitTitle} · 예시 문제
          </ThemedText>
        </View>

        <ThemedText variant="question" style={[styles.center, styles.prompt]}>
          {question.prompt}
        </ThemedText>

        <View accessibilityRole="radiogroup" style={styles.grid}>
          {question.options.map((option, i) => (
            <AnswerOption key={option} label={option} state={optionState(i)} onPress={() => setSelected(i)} />
          ))}
        </View>

        {checked && (
          <View
            style={[
              styles.feedback,
              { backgroundColor: (isCorrect ? components.feedbackCorrect : components.feedbackWrong).backgroundColor },
            ]}>
            <ThemedText variant="headline" tone={isCorrect ? 'successInk' : 'dangerInk'}>
              {isCorrect ? '맞았어요' : '아쉬워요, 정답은 ' + question.options[question.answerIndex]}
            </ThemedText>
            <ThemedText variant="subhead" tone={isCorrect ? 'successInk' : 'dangerInk'}>
              {question.explanation}
            </ThemedText>
            <ThemedText variant="caption" tone="inkMuted">
              근거: 대화 #{question.evidenceTurn}
            </ThemedText>
          </View>
        )}
      </ScrollView>

      <View style={[styles.footer, { paddingBottom: insets.bottom + spacing.xl }]}>
        <Button
          title={!checked ? '정답 확인' : last ? '결과 보기' : '다음 문제'}
          disabled={selected === null}
          onPress={onPrimary}
        />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.canvas },
  content: { flexGrow: 1, padding: spacing.xl, gap: spacing['2xl'], justifyContent: 'center' },
  meta: { alignItems: 'center', gap: spacing.sm },
  selfCenter: { alignSelf: 'center' },
  center: { textAlign: 'center' },
  prompt: { paddingHorizontal: spacing.sm },
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.md },
  feedback: {
    gap: spacing.xs,
    borderRadius: components.feedbackCorrect.rounded,
    borderCurve: 'continuous',
    padding: components.feedbackCorrect.padding,
  },
  footer: { paddingHorizontal: spacing.xl, paddingTop: spacing.md },
  done: { padding: spacing.xl, justifyContent: 'space-between' },
  doneBody: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: spacing.md },
  doneIcon: {
    width: 72,
    height: 72,
    borderRadius: components.iconButton.rounded,
    backgroundColor: colors.primary,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
