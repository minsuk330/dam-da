import type { ReactNode } from 'react';
import { StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';

import { Icon } from '@/components/icon';
import { ThemedText } from '@/components/themed-text';
import { colors, components, spacing } from '@/theme';

const tones = {
  warning: { background: colors.warningTint, ink: 'warningInk' },
  danger: { background: colors.dangerTint, ink: 'dangerInk' },
} as const;

/** 주의 문구 상자. 진한 경고색 면 대신 옅은 면 + 진한 글자. */
export function Notice({
  tone = 'warning',
  children,
  style,
}: {
  tone?: keyof typeof tones;
  children: ReactNode;
  style?: StyleProp<ViewStyle>;
}) {
  const { background, ink } = tones[tone];
  return (
    <View accessibilityRole="alert" style={[styles.notice, { backgroundColor: background }, style]}>
      <Icon name="alert-circle" size="md" color={colors[ink]} />
      <ThemedText variant="subhead" tone={ink} style={styles.text}>
        {children}
      </ThemedText>
    </View>
  );
}

const styles = StyleSheet.create({
  notice: {
    flexDirection: 'row',
    gap: spacing.sm,
    padding: spacing.lg,
    borderRadius: components.feedbackWrong.rounded,
  },
  text: { flex: 1 },
});
