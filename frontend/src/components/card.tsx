import { View, type StyleProp, type ViewStyle } from 'react-native';
import type { ReactNode } from 'react';

import { components } from '@/theme';

const variants = {
  surface: components.card,
  lavender: components.cardLavender,
  hero: components.cardHero,
} as const;

/** DESIGN.md의 card / card-lavender / card-hero. 내용은 children으로 받는다. */
export function Card({
  variant = 'surface',
  style,
  children,
}: {
  variant?: keyof typeof variants;
  style?: StyleProp<ViewStyle>;
  children: ReactNode;
}) {
  const token = variants[variant];
  return (
    <View
      style={[
        {
          backgroundColor: token.backgroundColor,
          borderRadius: token.rounded,
          borderCurve: 'continuous',
          padding: token.padding,
        },
        style,
      ]}>
      {children}
    </View>
  );
}
