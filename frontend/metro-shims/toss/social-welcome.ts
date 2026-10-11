/**
 * 토스 인앱 빌드 전용(#149): 웹 소셜 로그인 화면(src/screens/welcome/social.tsx)을 번들에서 뺀다(서비스 오픈 정책 2-3).
 * 토스 인앱의 로그인 전 화면은 익명 식별키로 들어가는 동안의 준비 화면뿐이다. metro.config.js가 바꿔 끼운다.
 */
export function SocialWelcome() {
  return null;
}
