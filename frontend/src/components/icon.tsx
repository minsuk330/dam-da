import Feather from '@expo/vector-icons/Feather';
import type { ComponentProps } from 'react';

import { colors } from '@/theme';

export type IconName = ComponentProps<typeof Feather>['name'];

/** 앱의 유일한 아이콘 세트(Feather, 얇은 선). 이모지를 아이콘으로 쓰지 않는다. */
export function Icon({
  name,
  size = 20,
  color = colors.ink,
}: {
  name: IconName;
  size?: number;
  color?: string;
}) {
  return <Feather name={name} size={size} color={color} />;
}
