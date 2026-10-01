import { StyleSheet, View } from 'react-native';

import { mockEnabled } from '@/api/mock';
import { ThemedText } from '@/components/themed-text';
import { colors, spacing } from '@/theme';

/**
 * 배포 빌드가 mock 데이터로 돌고 있으면 화면 맨 위에 띠를 둔다(데모 중 실수 방지).
 * 화면 위에 겹쳐 그리면 헤더 제목·버튼을 가리므로 한 줄을 차지하게 한다. 개발 서버는 기본이 mock이라 두지 않는다.
 */
export function MockBadge() {
  if (!mockEnabled || __DEV__) return null;
  return (
    <View accessibilityRole="alert" style={styles.strip}>
      <ThemedText variant="caption" tone="warningInk">
        MOCK 데이터 · 실제 서버 아님
      </ThemedText>
    </View>
  );
}

const styles = StyleSheet.create({
  strip: { alignItems: 'center', paddingVertical: spacing.xs, backgroundColor: colors.warningTint },
});
