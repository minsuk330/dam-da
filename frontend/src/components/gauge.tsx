import { useEffect } from 'react';
import { Text, View, type StyleProp, type ViewStyle } from 'react-native';
import Animated, { Easing, useAnimatedStyle, useReducedMotion, useSharedValue, withTiming } from 'react-native-reanimated';

import { ThemedText } from '@/components/themed-text';
import { components, motion, spacing } from '@/theme';

const sizes = {
  md: { height: components.gauge.height, labelVariant: 'caption', labelTone: 'inkSecondary' },
  lg: { height: components.gaugeLarge.height, labelVariant: 'subhead', labelTone: 'ink' },
} as const;

/** 목표 유지율을 모를 때 쓰는 값(FSRS 기본 목표 유지율). */
export const DEFAULT_TARGET_RETENTION = 0.9;
/** 목표보다 이만큼 아래까지는 "복습할 때", 그 아래는 "많이 잊음"이다. */
const REVIEW_BAND = 0.15;

/** DESIGN.md 기억 게이지의 세 단계. 기준은 그 항목의 목표 유지율이다. */
export type GaugeLevel = 'good' | 'review' | 'low';

const levels = {
  good: { track: components.gauge, fill: components.gaugeFill, label: '잘 기억해요' },
  review: { track: components.gaugeReview, fill: components.gaugeReviewFill, label: '복습할 때예요' },
  low: { track: components.gaugeLow, fill: components.gaugeLowFill, label: '많이 잊었어요' },
} as const;

/** 0~1 확률을 반올림한 정수 퍼센트. */
export function percent(value: number) {
  return Math.round(Math.min(1, Math.max(0, value)) * 100);
}

export function gaugeLevel(value: number, target = DEFAULT_TARGET_RETENTION): GaugeLevel {
  if (value >= target) return 'good';
  return value >= target - REVIEW_BAND ? 'review' : 'low';
}

/**
 * DESIGN.md의 기억 게이지. `value`는 지금 떠올릴 확률 R(0~1), `target`은 목표 유지율이다.
 * 색은 목표 대비 세 단계로 바뀌고 같은 색의 라벨 칩이 붙는다. null이면 "아직 확인 전"이며 0%와 다르게 그린다.
 */
export function Gauge({
  value,
  target = DEFAULT_TARGET_RETENTION,
  label = '지금 기억할 확률',
  size = 'md',
  style,
}: {
  value: number | null;
  target?: number;
  label?: string;
  size?: keyof typeof sizes;
  style?: StyleProp<ViewStyle>;
}) {
  const { height, labelVariant, labelTone } = sizes[size];
  const now = value === null ? null : percent(value);
  const level = value === null ? null : levels[gaugeLevel(value, target)];

  return (
    <View
      accessible
      accessibilityRole="progressbar"
      accessibilityLabel={level ? `${label}, ${level.label}` : `${label}, 아직 확인 전`}
      accessibilityValue={now !== null ? { min: 0, max: 100, now, text: `${now}%` } : { text: '아직 확인 전' }}
      style={[{ gap: spacing.xs }, style]}>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.sm }}>
        <ThemedText variant={labelVariant} tone={level ? labelTone : 'inkMuted'} style={{ flex: 1 }} numberOfLines={1}>
          {label}
        </ThemedText>
        {level && (
          <View
            style={{
              backgroundColor: level.track.backgroundColor,
              borderRadius: level.track.rounded,
              paddingHorizontal: spacing.sm,
              paddingVertical: 2, // 칩보다 낮은 인라인 라벨: 퍼센트 글자 높이에 맞춘다
            }}>
            <Text style={[level.track.typography, { color: level.track.textColor }]}>{level.label}</Text>
          </View>
        )}
        <ThemedText variant={labelVariant} tone={level ? labelTone : 'inkMuted'}>
          {now !== null ? `${now}%` : '아직 확인 전'}
        </ThemedText>
      </View>
      <View
        style={{
          height,
          borderRadius: components.gauge.rounded,
          backgroundColor: level ? level.track.backgroundColor : components.gaugeUnchecked.backgroundColor,
          overflow: 'hidden',
        }}>
        {level && now !== null && <Fill percent={now} color={level.fill.backgroundColor} rounded={level.fill.rounded} />}
      </View>
    </View>
  );
}

/**
 * 게이지 채움. 처음 보이거나 값이 바뀌면 지금 값까지 차오른다(DESIGN.md 움직임).
 * 기기의 움직임 줄이기 설정이 켜져 있으면 바로 그린다.
 */
function Fill({ percent: target, color, rounded }: { percent: number; color: string; rounded: number }) {
  const reduceMotion = useReducedMotion();
  const width = useSharedValue(reduceMotion ? target : 0);

  useEffect(() => {
    width.value = reduceMotion ? target : withTiming(target, { duration: motion.gauge, easing: Easing.out(Easing.cubic) });
  }, [reduceMotion, target, width]);

  const animated = useAnimatedStyle(() => ({ width: `${width.value}%` }));
  return <Animated.View style={[{ height: '100%', borderRadius: rounded, backgroundColor: color }, animated]} />;
}
