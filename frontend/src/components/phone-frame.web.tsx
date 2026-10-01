import type { ReactNode } from 'react';
import { StyleSheet, useWindowDimensions, View } from 'react-native';

import { colors, phone, radius } from '@/theme';

// 휴대폰 바깥 배경. 앱 화면이 아니라 시연용 무대라 토큰에 두지 않는다.
const STAGE = '#D4D7DE';

/**
 * 웹 시연용: 넓은 화면에서는 가운데 390×844 휴대폰 영역 안에 앱을 그린다.
 * 휴대폰 폭 브라우저에서는 그대로 전체 화면을 쓴다.
 */
export function PhoneFrame({ children }: { children: ReactNode }) {
  const { width, height } = useWindowDimensions();
  if (width <= phone.width + 40) {
    return <View style={styles.fill}>{children}</View>;
  }
  return (
    <View style={styles.stage}>
      <View style={[styles.device, { height: Math.min(phone.height, height - 48) }]}>{children}</View>
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
