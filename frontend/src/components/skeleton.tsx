import { useEffect } from 'react';
import { StyleSheet, View, type DimensionValue, type StyleProp, type ViewStyle } from 'react-native';
import Animated, {
  cancelAnimation,
  Easing,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withRepeat,
  withTiming,
} from 'react-native-reanimated';

import { Card } from '@/components/card';
import { colors, components, motion, radius, spacing } from '@/theme';

const shapes = {
  /** 글자 한 줄. 높이는 본문 줄 높이. */
  line: { height: components.icon.size, borderRadius: radius.sm },
  /** 원형 아이콘 자리. */
  circle: { width: components.iconCircle.size, height: components.iconCircle.size, borderRadius: components.iconCircle.rounded },
  /** 카드 한 장. 높이는 호출하는 쪽이 `height`로 준다. */
  block: { borderRadius: components.card.rounded },
} as const;

/**
 * 불러오는 중에 실제 레이아웃 자리를 미리 보여주는 회색 면(DESIGN.md 네 상태).
 * 천천히 흐려졌다 돌아오고, 움직임 줄이기가 켜져 있으면 멈춰 있다. 스크린 리더에는 숨긴다(화면이 "불러오는 중"을 알린다).
 */
export function Skeleton({
  shape = 'line',
  width,
  height,
  style,
}: {
  shape?: keyof typeof shapes;
  width?: DimensionValue;
  height?: DimensionValue;
  style?: StyleProp<ViewStyle>;
}) {
  const reduceMotion = useReducedMotion();
  const fade = useSharedValue(1);

  useEffect(() => {
    if (reduceMotion) return;
    fade.value = withRepeat(withTiming(0.5, { duration: motion.pulse, easing: Easing.inOut(Easing.ease) }), -1, true);
    return () => cancelAnimation(fade);
  }, [fade, reduceMotion]);

  const animated = useAnimatedStyle(() => ({ opacity: fade.value }));
  return (
    <Animated.View
      accessibilityElementsHidden
      importantForAccessibility="no-hide-descendants"
      style={[{ backgroundColor: colors.outline }, shapes[shape], width !== undefined && { width }, height !== undefined && { height }, animated, style]}
    />
  );
}

/** 구분선 목록(원형 아이콘 + 두 줄)을 불러오는 동안의 자리. */
export function SkeletonList({ rows = 3, style }: { rows?: number; style?: StyleProp<ViewStyle> }) {
  return (
    <Card style={[styles.list, style]}>
      {Array.from({ length: rows }, (_, i) => (
        <View key={i} style={[styles.row, i > 0 && styles.divided]}>
          <Skeleton shape="circle" />
          <View style={styles.text}>
            <Skeleton width="60%" />
            <Skeleton width="40%" height={components.iconSmall.size} />
          </View>
        </View>
      ))}
    </Card>
  );
}

const styles = StyleSheet.create({
  list: { paddingVertical: spacing.xs },
  row: { flexDirection: 'row', alignItems: 'center', gap: spacing.md, paddingVertical: spacing.lg },
  divided: { borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.outline },
  text: { flex: 1, gap: spacing.sm },
});
