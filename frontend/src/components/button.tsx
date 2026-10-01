import { ActivityIndicator, Pressable, Text, type StyleProp, type ViewStyle } from 'react-native';

import { components, opacity, spacing } from '@/theme';

const variants = {
  primary: components.buttonPrimary,
  secondary: components.buttonSecondary,
} as const;

/** DESIGN.md의 button-primary / button-secondary. 높이 56 알약형, 기본 전체 폭. 주 버튼은 화면당 하나. */
export function Button({
  title,
  variant = 'primary',
  disabled,
  loading,
  onPress,
  style,
}: {
  title: string;
  variant?: keyof typeof variants;
  disabled?: boolean;
  loading?: boolean;
  onPress?: () => void;
  style?: StyleProp<ViewStyle>;
}) {
  const token = variants[variant];
  const inactive = disabled || loading;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={title}
      accessibilityState={{ disabled: !!inactive, busy: !!loading }}
      disabled={inactive}
      onPress={onPress}
      style={({ pressed }) => [
        {
          height: token.height,
          borderRadius: token.rounded,
          backgroundColor:
            pressed && variant === 'primary' ? components.buttonPrimaryPressed.backgroundColor : token.backgroundColor,
          alignItems: 'center',
          justifyContent: 'center',
          paddingHorizontal: spacing['2xl'],
          opacity: disabled ? opacity.disabled : 1,
        },
        style,
      ]}>
      {loading ? (
        <ActivityIndicator color={token.textColor} />
      ) : (
        <Text style={[token.typography, { color: token.textColor }]}>{title}</Text>
      )}
    </Pressable>
  );
}
