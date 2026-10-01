import { View, type StyleProp, type ViewStyle } from 'react-native';
import type { ReactNode } from 'react';

import { components } from '@/theme';

const variants = {
  surface: { base: components.card, pressed: components.cardPressed },
  lavender: { base: components.cardLavender, pressed: components.cardLavender },
  hero: { base: components.cardHero, pressed: components.cardHeroPressed },
} as const;

/**
 * DESIGN.md의 card / card-lavender / card-hero. 내용은 children으로 받는다.
 * 눌리는 카드는 `pressed`를 넘기면 투명도 대신 배경색으로 눌림을 보여준다(card-pressed / card-hero-pressed).
 */
export function Card({
  variant = 'surface',
  pressed = false,
  style,
  children,
}: {
  variant?: keyof typeof variants;
  pressed?: boolean;
  style?: StyleProp<ViewStyle>;
  children: ReactNode;
}) {
  const { base, pressed: down } = variants[variant];
  return (
    <View
      style={[
        {
          backgroundColor: pressed ? down.backgroundColor : base.backgroundColor,
          borderRadius: base.rounded,
          borderCurve: 'continuous',
          padding: base.padding,
        },
        style,
      ]}>
      {children}
    </View>
  );
}
