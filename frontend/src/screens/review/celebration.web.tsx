import { DotLottieReact, type DotLottie } from '@lottiefiles/dotlottie-react';
import { Asset } from 'expo-asset';
import { useEffect, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { useReducedMotion } from 'react-native-reanimated';

import confetti from '@/assets/animations/confetti.lottie';
import { components } from '@/theme';

/**
 * 웹용 축하 애니메이션. lottie-react-native의 웹 구현은 resizeMode를 무시하므로
 * dotLottie 플레이어를 직접 써서 영역을 채우도록 잘라 맞춘다(cover). 네이티브는 celebration.tsx.
 */
export function Celebration() {
  const reduceMotion = useReducedMotion();
  const [player, setPlayer] = useState<DotLottie | null>(null);
  const [finished, setFinished] = useState(false);

  useEffect(() => {
    if (!player) return;
    const finish = () => setFinished(true);
    player.addEventListener('complete', finish);
    player.addEventListener('loadError', finish);
    return () => {
      player.removeEventListener('complete', finish);
      player.removeEventListener('loadError', finish);
    };
  }, [player]);

  if (reduceMotion || finished) return null;

  return (
    <View style={styles.overlay}>
      <DotLottieReact
        src={Asset.fromModule(confetti).uri}
        autoplay
        loop={false}
        layout={{ fit: 'cover' }}
        dotLottieRefCallback={setPlayer}
        style={{ width: '100%', height: '100%' }}
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
