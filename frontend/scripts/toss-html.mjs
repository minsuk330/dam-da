// 토스 인앱 번들의 index.html을 고친다(#149). 웹 빌드(Vercel)는 그대로 둔다. `npm run toss:build`가 export 뒤에 부른다.
// - 손가락 확대·축소를 JS가 뜨기 전부터 막는다(비게임 출시 가이드: 지도 외 확대·축소 비활성화). TossBridge도 같은 값을 넣지만
//   그건 JS가 실행된 뒤이고, 검수는 index.html도 본다.
// - 문서 언어를 한국어로 둔다.
import { readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const file = join(process.argv[2] ?? 'dist-toss', 'index.html');
const viewport = 'width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no, viewport-fit=cover';

let html = readFileSync(file, 'utf8');
const replacements = [
  [/<html lang="[^"]*">/, '<html lang="ko">'],
  [/<meta name="viewport" content="[^"]*"\s*\/?>/, `<meta name="viewport" content="${viewport}" />`],
];
for (const [pattern, value] of replacements) {
  if (!pattern.test(html)) {
    console.error(`${file}에서 ${pattern}을 찾지 못했어요. Expo 웹 템플릿이 바뀌었는지 확인해 주세요.`);
    process.exit(1);
  }
  html = html.replace(pattern, value);
}
writeFileSync(file, html);
console.log(`${file}: lang=ko, 확대·축소 막음`);
