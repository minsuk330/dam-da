import Feather from '@expo/vector-icons/Feather';
import type { ComponentProps } from 'react';

import { colors, components } from '@/theme';

export type IconName = ComponentProps<typeof Feather>['name'];

/** DESIGN.md 아이콘 크기: icon-small / icon / icon-large / icon-xl. */
const sizes = {
  sm: components.iconSmall.size,
  md: components.icon.size,
  lg: components.iconLarge.size,
  xl: components.iconXl.size,
} as const;

/** 앱의 유일한 아이콘 세트(Feather, 얇은 선). 이모지를 아이콘으로 쓰지 않는다. */
export function Icon({
  name,
  size = 'md',
  color = colors.ink,
}: {
  name: IconName;
  size?: keyof typeof sizes;
  color?: string;
}) {
  return <Feather name={name} size={sizes[size]} color={color} />;
}
