import LottieView, { type LottieViewProps } from 'lottie-react-native';
import { useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { useReducedMotion } from 'react-native-reanimated';

import confetti from '@/assets/animations/confetti.lottie';
import { components } from '@/theme';

/**
 * 축하 애니메이션(DESIGN.md Components "축하 애니메이션"): 화면 위쪽에 한 번 재생하고 지운다.
 * 터치를 막지 않고, 움직임 줄이기 설정이면 그리지 않는다. 웹은 celebration.web.tsx.
 */
export function Celebration() {
  const reduceMotion = useReducedMotion();
  const [finished, setFinished] = useState(false);
  if (reduceMotion || finished) return null;

  return (
    <View style={styles.overlay}>
      {/* 자산 모듈 ID(숫자)도 받지만(parsePossibleSources) 타입 정의에는 빠져 있다. */}
      <LottieView
        source={confetti as unknown as LottieViewProps['source']}
        autoPlay
        loop={false}
        resizeMode="cover"
        onAnimationFinish={() => setFinished(true)}
        style={StyleSheet.absoluteFill}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  overlay: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    height: components.celebration.size,
    pointerEvents: 'none',
  },
});
