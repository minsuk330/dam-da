import { StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { mockEnabled } from '@/api/mock';
import { Chip } from '@/components/chip';
import { spacing } from '@/theme';

/**
 * 배포 빌드가 mock 데이터로 돌고 있으면 화면 위에 표시한다(데모 중 실수 방지).
 * 개발 서버는 기본이 mock이라 표시하지 않는다. 화면 조작을 막지 않도록 터치는 통과시킨다.
 */
export function MockBadge() {
  const insets = useSafeAreaInsets();
  if (!mockEnabled || __DEV__) return null;
  return (
    <View pointerEvents="none" style={[styles.overlay, { top: insets.top + spacing['2xs'] }]}>
      <Chip variant="warning" label="MOCK 데이터 · 실제 서버 아님" style={styles.chip} />
    </View>
  );
}

const styles = StyleSheet.create({
  overlay: { position: 'absolute', left: 0, right: 0 },
  chip: { alignSelf: 'center' },
});
