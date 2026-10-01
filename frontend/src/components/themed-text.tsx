import { Text, type TextProps } from 'react-native';

import { colors, typography } from '@/theme';

type Tone = 'ink' | 'inkSecondary' | 'inkMuted' | 'primaryInk' | 'onPrimary' | 'successInk' | 'dangerInk' | 'warningInk' | 'kakaoInk';

/** 화면은 fontSize를 직접 쓰지 않고 이 컴포넌트의 variant로 글자를 고른다. */
export function ThemedText({
  variant = 'body',
  tone = 'ink',
  style,
  ...props
}: TextProps & { variant?: keyof typeof typography; tone?: Tone }) {
  return <Text style={[typography[variant], { color: colors[tone] }, style]} {...props} />;
}
