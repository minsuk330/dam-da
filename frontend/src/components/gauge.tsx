import { View, type StyleProp, type ViewStyle } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { components, spacing } from '@/theme';

const sizes = {
  md: { track: components.gauge, labelVariant: 'caption', labelTone: 'inkSecondary' },
  lg: { track: components.gaugeLarge, labelVariant: 'subhead', labelTone: 'ink' },
} as const;

/** 0~1 확률을 반올림한 정수 퍼센트. */
export function percent(value: number) {
  return Math.round(Math.min(1, Math.max(0, value)) * 100);
}

/**
 * DESIGN.md의 기억 게이지(gauge / gauge-large / gauge-fill / gauge-unchecked).
 * `value`는 지금 떠올릴 확률 R(0~1). null이면 아직 등급이 없는 "아직 확인 전"이며 0%와 다르게 그린다.
 */
export function Gauge({
  value,
  label = '지금 기억할 확률',
  size = 'md',
  style,
}: {
  value: number | null;
  label?: string;
  size?: keyof typeof sizes;
  style?: StyleProp<ViewStyle>;
}) {
  const { track, labelVariant, labelTone } = sizes[size];
  const now = value === null ? null : percent(value);
  const checked = now !== null;

  return (
    <View
      accessible
      accessibilityRole="progressbar"
      accessibilityLabel={checked ? label : `${label}, 아직 확인 전`}
      accessibilityValue={checked ? { min: 0, max: 100, now, text: `${now}%` } : { text: '아직 확인 전' }}
      style={[{ gap: spacing.xs }, style]}>
      <View style={{ flexDirection: 'row', justifyContent: 'space-between', gap: spacing.sm }}>
        <ThemedText variant={labelVariant} tone={checked ? labelTone : 'inkMuted'} style={{ flexShrink: 1 }}>
          {label}
        </ThemedText>
        <ThemedText variant={labelVariant} tone={checked ? labelTone : 'inkMuted'}>
          {checked ? `${now}%` : '아직 확인 전'}
        </ThemedText>
      </View>
      <View
        style={{
          height: track.height,
          borderRadius: track.rounded,
          backgroundColor: checked ? track.backgroundColor : components.gaugeUnchecked.backgroundColor,
          overflow: 'hidden',
        }}>
        {now !== null && (
          <View
            style={{
              width: `${now}%`,
              height: '100%',
              borderRadius: components.gaugeFill.rounded,
              backgroundColor: components.gaugeFill.backgroundColor,
            }}
          />
        )}
      </View>
    </View>
  );
}
