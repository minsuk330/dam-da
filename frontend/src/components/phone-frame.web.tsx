import type { ReactNode } from 'react';
import { StyleSheet, useWindowDimensions, View } from 'react-native';

import { colors, components, phone, radius, spacing } from '@/theme';

// 휴대폰 바깥 배경(시연용 무대). DESIGN.md phone-stage.
const STAGE = components.phoneStage.backgroundColor;

/**
 * 웹 시연용: 넓은 화면에서는 가운데 390×844 휴대폰 영역 안에 앱을 그린다.
 * 휴대폰 폭 브라우저에서는 그대로 전체 화면을 쓴다.
 */
export function PhoneFrame({ children }: { children: ReactNode }) {
  const { width, height } = useWindowDimensions();
  // 좌우 xl 여백이 들어가지 않을 만큼 좁으면 휴대폰 틀 없이 전체 화면.
  if (width <= phone.width + spacing.xl * 2) {
    return <View style={styles.fill}>{children}</View>;
  }
  return (
    <View style={styles.stage}>
      <View style={[styles.device, { height: Math.min(phone.height, height - spacing['2xl'] * 2) }]}>{children}</View>
    </View>
  );
}

const styles = StyleSheet.create({
  fill: { flex: 1, backgroundColor: colors.canvas },
  stage: { flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: STAGE },
  device: {
    width: phone.width,
    overflow: 'hidden',
    borderRadius: radius.xl + 16,
    backgroundColor: colors.canvas,
  },
});
