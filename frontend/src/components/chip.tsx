import { Text, View, type StyleProp, type ViewStyle } from 'react-native';

import { components, spacing } from '@/theme';

const variants = {
  status: components.chipStatus,
  soft: components.chipSoft,
  warning: components.chipWarning,
} as const;

/** DESIGN.md의 chip-status / chip-soft / chip-warning. 알약형 짧은 라벨. */
export function Chip({
  variant = 'soft',
  label,
  style,
}: {
  variant?: keyof typeof variants;
  label: string;
  style?: StyleProp<ViewStyle>;
}) {
  const token = variants[variant];
  return (
    <View
      style={[
        {
          alignSelf: 'flex-start',
          backgroundColor: token.backgroundColor,
          borderRadius: token.rounded,
          paddingHorizontal: spacing.md,
          paddingVertical: spacing.xs,
        },
        style,
      ]}>
      <Text style={[token.typography, { color: token.textColor }]}>{label}</Text>
    </View>
  );
}
