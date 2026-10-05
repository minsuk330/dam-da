/**
 * 토스 인앱(앱인토스) 미니앱 빌드인지 (#149). `npm run toss:build`·`npm run toss:web`이 EXPO_PUBLIC_TOSS=true로 빌드한다.
 * 토스 안에서는 휴대폰 틀 없이 전체 화면을 쓰고, 토스 로그인만 쓸 수 있다(앱인토스 서비스 오픈 정책 2-3).
 */
export const inToss = process.env.EXPO_PUBLIC_TOSS === 'true'
