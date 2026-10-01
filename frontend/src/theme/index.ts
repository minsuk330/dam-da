// 디자인 토큰의 유일한 진입점. 값은 DESIGN.md → tokens.ts(생성)에서 온다.
export { colors, components, radius, spacing, typography } from './tokens';

/** 비활성·제외 상태의 불투명도 (DESIGN.md Components "눌림·비활성·제외 상태"). design.md 스키마에 투명도 토큰이 없어 여기에 둔다. */
export const opacity = { disabled: 0.4, excluded: 0.6 } as const;

/**
 * 움직임 시간(ms). design.md 스키마에 움직임 토큰이 없어 여기 둔다(DESIGN.md 움직임 규칙).
 * gauge: 게이지가 차오르는 시간, pulse: 불러오는 중 skeleton이 한 번 흐려졌다 돌아오는 시간.
 */
export const motion = { gauge: 600, pulse: 900 } as const;

/** 웹에서 앱을 그리는 휴대폰 영역 크기 (DESIGN.md Overview의 기준 화면). */
export const phone = { width: 390, height: 844 } as const;

/** expo-font useFonts 입력. tokens.ts의 fontFamilies와 이름이 같아야 한다. */
export const fonts = {
  'SUIT-Regular': require('@/assets/fonts/SUIT-Regular.otf'),
  'SUIT-Medium': require('@/assets/fonts/SUIT-Medium.otf'),
  'SUIT-SemiBold': require('@/assets/fonts/SUIT-SemiBold.otf'),
  'SUIT-Bold': require('@/assets/fonts/SUIT-Bold.otf'),
};
