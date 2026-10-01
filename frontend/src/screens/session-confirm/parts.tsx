import type { ReactNode } from 'react';
import { StyleSheet, View } from 'react-native';

import { Icon } from '@/components/icon';
import { ThemedText } from '@/components/themed-text';
import { colors, components, spacing } from '@/theme';

/** 단계 제목: "1/3" 진행 막대(게이지 토큰) + 큰 제목 + 설명. */
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
      <ThemedText variant="caption" tone="inkMuted">
        {step}/{total}
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

/** 주의 문구 상자. 진한 경고색 면 대신 옅은 면 + 진한 글자. */
export function Notice({ tone = 'warning', children }: { tone?: 'warning' | 'danger'; children: ReactNode }) {
  return (
    <View accessibilityRole="alert" style={[styles.notice, tone === 'danger' && styles.noticeDanger]}>
      <Icon name="alert-circle" size="md" color={tone === 'danger' ? colors.dangerInk : colors.warningInk} />
      <ThemedText variant="subhead" tone={tone === 'danger' ? 'dangerInk' : 'warningInk'} style={styles.noticeText}>
        {children}
      </ThemedText>
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
  notice: {
    flexDirection: 'row',
    gap: spacing.sm,
    padding: spacing.lg,
    borderRadius: components.feedbackWrong.rounded,
    backgroundColor: colors.warningTint,
  },
  noticeDanger: { backgroundColor: colors.dangerTint },
  noticeText: { flex: 1 },
});
