/**
 * 토스 인앱 빌드 전용(#149): pixi.js를 번들에서 뺀다. pixi 렌더러는 셰이더 코드를 new Function으로 만들고,
 * 앱인토스 검수는 번들 안의 new Function/eval을 반려한다. 토스 빌드는 지식 그래프 대신 목록만 보여준다. metro.config.js가 바꿔 끼운다.
 */
throw new Error('토스 인앱 빌드에는 그래프 렌더러(pixi.js)가 없어요.');
