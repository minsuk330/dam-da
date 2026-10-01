import { StyleSheet, View } from 'react-native';

import { Card } from '@/components/card';
import { Icon, type IconName } from '@/components/icon';
import { ThemedText } from '@/components/themed-text';
import { colors, components, spacing } from '@/theme';

/** 레퍼런스 홈의 2열 통계 카드: 왼쪽 위 원형 아이콘, 큰 숫자 + 단위, 아래 설명. */
export function StatCard({
  icon,
  value,
  unit,
  label,
  variant,
}: {
  icon: IconName;
  value: string;
  unit: string;
  label: string;
  variant: 'surface' | 'lavender';
}) {
  const lavender = variant === 'lavender';
  return (
    <Card variant={variant} style={styles.card}>
      <View style={[styles.iconCircle, { backgroundColor: lavender ? colors.primary : colors.surfaceSoft }]}>
        <Icon name={icon} size={20} color={lavender ? colors.onPrimary : colors.ink} />
      </View>
      <View style={styles.valueRow}>
        <ThemedText variant="stat">{value}</ThemedText>
        <ThemedText variant="caption" tone={lavender ? 'inkSecondary' : 'inkMuted'}>
          {unit}
        </ThemedText>
      </View>
      <ThemedText variant="subhead" tone={lavender ? 'inkSecondary' : 'inkMuted'}>
        {label}
      </ThemedText>
    </Card>
  );
}

const styles = StyleSheet.create({
  card: { flex: 1, minHeight: 152, justifyContent: 'space-between', gap: spacing.sm },
  iconCircle: {
    width: 44,
    height: 44,
    borderRadius: components.iconButtonPrimary.rounded,
    alignItems: 'center',
    justifyContent: 'center',
  },
  valueRow: { flexDirection: 'row', alignItems: 'baseline', justifyContent: 'flex-end', gap: spacing.xs },
});
