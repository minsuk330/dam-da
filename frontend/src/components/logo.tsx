import Svg, { G, Path, Rect } from 'react-native-svg';

import { colors } from '@/theme';

/**
 * 서비스 로고: 대화(말풍선) 안에서 확인(체크)하고, AI(라벤더 반짝임)가 돕는다. 원본은 assets/brand/logo.svg.
 * 둥근 사각형 바탕까지 그린 한 덩어리라 크기만 받는다. 이름과 함께 쓰므로 스크린 리더에는 숨긴다.
 */
export function Logo({ size }: { size: number }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 64 64">
      <Rect width={64} height={64} rx={16} fill={colors.primary} />
      <G transform="translate(32 33) scale(0.9) translate(-32 -33)">
        <Path
          d="M19 14h26a9 9 0 0 1 9 9v13a9 9 0 0 1-9 9H30l-10 7.5V45h-1a9 9 0 0 1-9-9V23a9 9 0 0 1 9-9z"
          fill={colors.onPrimary}
        />
        <Path
          d="M22.5 29.5l6.5 6.5 13.5-13.5"
          fill="none"
          stroke={colors.primary}
          strokeWidth={5.5}
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        <Path
          d="M53 5.5c.6 3.6 1.9 4.9 5.5 5.5-3.6.6-4.9 1.9-5.5 5.5-.6-3.6-1.9-4.9-5.5-5.5 3.6-.6 4.9-1.9 5.5-5.5z"
          fill={colors.lavender}
        />
      </G>
    </Svg>
  );
}
