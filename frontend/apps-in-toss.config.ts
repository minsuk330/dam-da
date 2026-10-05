import { defineConfig } from '@apps-in-toss/web-framework/config';

/**
 * 토스 인앱(앱인토스) WebView 미니앱 설정 (#149). SDK 3.x 형식.
 * 웹 빌드(`npm run toss:build`)가 만든 dist-toss/를 그대로 .ait 아티팩트에 담는다.
 * appName은 앱인토스 콘솔에 등록한 이름과 같아야 한다(intoss://{appName}).
 */
export default defineConfig({
  appName: 'damda-ai',
  brand: {
    // DESIGN.md colors.primary. 이 파일은 앱 밖 빌드 설정이라 @/theme을 가져오지 않는다.
    primaryColor: '#2E5BE8',
  },
  // 뒤로가기는 토스 내비게이션 바만 쓴다. 앱 헤더 뒤로가기는 숨기고 TossBridge가 화면 기록을 따라간다(비게임 출시 가이드).
  navigationBar: {
    withBackButton: true,
  },
  webView: {
    bounces: false,
    pullToRefreshEnabled: false,
    overScrollMode: 'never',
    // 뒤로가기는 토스 내비게이션 바와 앱 헤더가 맡는다. 스와이프 뒤로가기가 웹 히스토리와 겹치지 않게 끈다.
    allowsBackForwardNavigationGestures: false,
  },
  permissions: [],
  webBundleDir: 'dist-toss',
});
