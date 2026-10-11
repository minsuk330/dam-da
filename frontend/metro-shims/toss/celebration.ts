/**
 * 토스 인앱 빌드 전용(#149): 복습 완료 축하 애니메이션(src/screens/review/celebration.web.tsx)을 번들에서 뺀다.
 * dotLottie 플레이어는 렌더러(WASM)를 실행할 때 jsDelivr·unpkg CDN에서 받아오는데, 앱인토스 검수는 외부에서 받은 코드를
 * 실행하는 기능을 반려한다. 토스 빌드는 애니메이션 없이 결과만 보여준다. metro.config.js가 바꿔 끼운다.
 */
export function Celebration() {
  return null;
}
