import { StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { components, spacing } from '@/theme';

/** 확인 단계 이름. 진행 막대 아래에 "1단계 · 메시지 확인"처럼 쓴다. */
const STEP_NAMES = ['메시지 확인', '복습할 내용', '공부 방법'];

/** 단계 제목: 진행 막대(게이지 토큰) + 단계 이름 + 큰 제목 + 설명. */
export function StepHeader({ step, total, title, description }: { step: number; total: number; title: string; description?: string }) {
  return (
    <View style={styles.header}>
      <View
        accessible
        accessibilityRole="progressbar"
        accessibilityLabel={`${total}단계 중 ${step}단계`}
        accessibilityValue={{ min: 0, max: total, now: step }}
        style={styles.steps}>
        {Array.from({ length: total }, (_, i) => (
          <View key={i} style={[styles.stepBar, i < step && styles.stepBarDone]} />
        ))}
      </View>
      <ThemedText variant="caption" tone="primaryInk">
        {step}단계 · {STEP_NAMES[step - 1] ?? `${step}/${total}`}
      </ThemedText>
      <ThemedText variant="title">{title}</ThemedText>
      {description && (
        <ThemedText variant="subhead" tone="inkMuted">
          {description}
        </ThemedText>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  header: { gap: spacing.sm },
  steps: { flexDirection: 'row', gap: spacing.xs },
  stepBar: {
    flex: 1,
    height: components.gauge.height,
    borderRadius: components.gauge.rounded,
    backgroundColor: components.gauge.backgroundColor,
  },
  stepBarDone: { backgroundColor: components.gaugeFill.backgroundColor },
});
