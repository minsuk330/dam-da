// https://docs.expo.dev/guides/customizing-metro
const path = require('path');
const { getDefaultConfig } = require('expo/metro-config');

const config = getDefaultConfig(__dirname);

// dotLottie 애니메이션(assets/animations/*.lottie)을 자산으로 번들한다.
config.resolver.assetExts.push('lottie');

// 토스 인앱 빌드(EXPO_PUBLIC_TOSS=true, #149): 앱인토스 검수는 번들 안에 eval·new Function이 있으면 반려한다
// ("외부에서 코드를 받아와 실행할 수 있는 코드"). 그런 코드가 든 모듈을 eval 없는 판으로 바꿔 끼우고, pixi.js는 뺀다.
// 바꾼 뒤 `npm run toss:build`가 번들을 검사한다(scripts/check-no-eval.mjs).
if (process.env.EXPO_PUBLIC_TOSS === 'true') {
  const shim = (name) => ({ type: 'sourceFile', filePath: path.join(__dirname, 'metro-shims/toss', name) });
  const replaced = [
    // Expo 비동기 로딩의 개발용 fetch + eval 경로. 운영 웹은 <script> 태그로 불러온다.
    [path.join('expo', 'src', 'async-require', 'fetchThenEvalJs.ts'), 'fetch-then-eval.ts'],
    // Node 환경용 eval('require') 분기가 있는 uuid(web).
    [path.join('expo-modules-core', 'src', 'uuid', 'index.web.ts'), 'uuid.web.ts'],
  ];
  config.resolver.resolveRequest = (context, moduleName, platform) => {
    if (moduleName === 'pixi.js' || moduleName.startsWith('pixi.js/')) return shim('pixi.ts');
    const resolution = context.resolveRequest(context, moduleName, platform);
    if (resolution.type === 'sourceFile') {
      const hit = replaced.find(([suffix]) => resolution.filePath.endsWith(suffix));
      if (hit) return shim(hit[1]);
    }
    return resolution;
  };
}

module.exports = config;
