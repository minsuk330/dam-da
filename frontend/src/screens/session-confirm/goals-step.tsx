import { useState } from 'react';
import { ActivityIndicator, StyleSheet, View } from 'react-native';

import type { Schemas } from '@/api/client';
import { useChooseGoals, useLearningGoals, useMemoryStrength } from '@/api/learning-sessions';
import { Button } from '@/components/button';
import { OptionRow } from '@/components/option-row';
import { Notice } from '@/components/notice';
import { ThemedText } from '@/components/themed-text';
import { colors, spacing } from '@/theme';

import { StepHeader } from './parts';

type Goal = Schemas['GoalView']['goal'];
type Strength = Schemas['Option']['strength'];

const DEFAULT_STRENGTH: Strength = 'UNDERSTAND';

/** 3단계: 학습 목표(최대 3개)와 기억 강도를 고른다. 저장하면 첫 학습 문제 생성이 시작된다. */
export function GoalsStep({ sessionId, onChosen }: { sessionId: number; onChosen: () => void }) {
  const goals = useLearningGoals(sessionId, true);
  const strength = useMemoryStrength(sessionId, true);
  const choose = useChooseGoals(sessionId);
  const [pickedGoals, setPickedGoals] = useState<Goal[] | null>(null);
  const [pickedStrength, setPickedStrength] = useState<Strength | null>(null);

  if (goals.isPending || strength.isPending) {
    return (
      <View style={styles.center}>
        <ActivityIndicator color={colors.primary} />
      </View>
    );
  }
  if (goals.isError || strength.isError) {
    return (
      <View style={styles.step}>
        <Notice tone="danger">학습 목표를 불러오지 못했어요.</Notice>
        <Button
          variant="secondary"
          title="다시 시도"
          onPress={() => {
            goals.refetch();
            strength.refetch();
          }}
        />
      </View>
    );
  }

  const selected = pickedGoals ?? goals.data.selected;
  const max = goals.data.maxSelected;
  const chosenStrength = pickedStrength ?? strength.data.current ?? DEFAULT_STRENGTH;

  function toggle(goal: Goal) {
    setPickedGoals(selected.includes(goal) ? selected.filter((g) => g !== goal) : [...selected, goal]);
  }

  return (
    <View style={styles.step}>
      <StepHeader
        step={3}
        total={3}
        title="어떻게 공부할까요?"
        description="고른 목표에 맞춰 첫 학습 문제를 만들어요."
      />

      <View style={styles.section}>
        <View style={styles.sectionHeader}>
          <ThemedText variant="headline">학습 목표</ThemedText>
          <ThemedText variant="caption" tone="inkMuted">
            {selected.length}/{max}개
          </ThemedText>
        </View>
        {goals.data.available.map((goal) => {
          const isSelected = selected.includes(goal.goal);
          return (
            <OptionRow
              key={goal.goal}
              multiple
              selected={isSelected}
              disabled={!isSelected && selected.length >= max}
              onPress={() => toggle(goal.goal)}>
              <ThemedText variant="headline">{goal.label}</ThemedText>
              <ThemedText variant="caption" tone="inkSecondary">
                {goal.description}
              </ThemedText>
            </OptionRow>
          );
        })}
      </View>

      <View style={styles.section}>
        <View style={styles.sectionHeader}>
          <ThemedText variant="headline">기억 강도</ThemedText>
          <ThemedText variant="caption" tone="inkMuted">
            항목 {strength.data.itemCount}개 기준
          </ThemedText>
        </View>
        {strength.data.options.map((option) => (
          <OptionRow
            key={option.strength}
            selected={option.strength === chosenStrength}
            onPress={() => setPickedStrength(option.strength)}>
            <ThemedText variant="headline">{option.label}</ThemedText>
            <ThemedText variant="caption" tone="inkSecondary">
              기억 목표 {Math.round(option.desiredRetention * 100)}% · 하루 약 {formatMinutes(option.dailyMinutes)}
            </ThemedText>
          </OptionRow>
        ))}
      </View>

      {choose.error && <Notice tone="danger">{choose.error.message}</Notice>}
      <Button
        title="첫 학습 문제 만들기"
        disabled={selected.length === 0}
        loading={choose.isPending}
        onPress={() => choose.mutate({ goals: selected, strength: chosenStrength }, { onSuccess: onChosen })}
      />
    </View>
  );
}

function formatMinutes(minutes: number) {
  return minutes < 1 ? `${Math.max(1, Math.round(minutes * 60))}초` : `${Math.round(minutes)}분`;
}

const styles = StyleSheet.create({
  step: { gap: spacing.md },
  center: { paddingVertical: spacing['3xl'], alignItems: 'center' },
  section: { gap: spacing.sm, marginTop: spacing.md },
  sectionHeader: { flexDirection: 'row', alignItems: 'baseline', justifyContent: 'space-between' },
});
