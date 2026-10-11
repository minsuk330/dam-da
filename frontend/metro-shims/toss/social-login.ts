/**
 * 토스 인앱 빌드 전용(#149): 웹 소셜 로그인(src/api/social-login.ts)을 번들에서 뺀다. 미니앱 안에서는 토스 로그인 외 로그인을
 * 쓸 수 없고(서비스 오픈 정책 2-3), 검수는 번들 안의 코드도 본다. 토스 인앱은 익명 식별키로 들어가므로 화면에서 부르지 않는다.
 * metro.config.js가 바꿔 끼운다.
 */
function unavailable(): never {
  throw new Error('토스 인앱에서는 쓰지 않아요.');
}

export const useLoginOptions = unavailable;
export const openLoginPage = unavailable;
export const exchangeCode = unavailable;
