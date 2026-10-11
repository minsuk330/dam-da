// 토스 인앱 번들 검사(#149): 앱인토스 검수는 번들 안의 코드도 본다. dist-toss의 JS에 아래 코드가 하나라도 있으면 실패한다.
// `npm run toss:build`가 .ait를 만들기 전에 부른다. 걸리면 metro.config.js의 토스 빌드 대체 모듈로 뺀다.
// - eval·new Function: 외부에서 받은 코드를 실행할 수 있는 코드(비게임 출시 가이드 보안 항목, 첫 검수 반려 사유)
// - 외부 CDN·WASM: 실행할 코드를 토스 밖에서 받아온다(dotLottie 플레이어가 jsDelivr·unpkg에서 WASM을 받았다)
// - location.assign·replace: 토스 밖 페이지로 이동(같은 항목의 예시)
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';

const root = process.argv[2] ?? 'dist-toss';
const rules = [
  ['eval/new Function', [/(?<![\w$.])eval\s*\(/, /new\s+Function\s*\(/, /(?<![\w$.])Function\s*\(\s*["'`]/, /set(Timeout|Interval)\s*\(\s*["'`]/]],
  ['외부 CDN·WASM 코드', [/WebAssembly\.(instantiate|compile)/, /\.wasm\b/, /\b(cdn\.jsdelivr\.net|unpkg\.com|cdnjs\.cloudflare\.com|esm\.sh|cdn\.skypack\.dev)\b/]],
  ['토스 밖으로 이동', [/location\.(assign|replace)\s*\(/]],
];

function* files(dir) {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name);
    if (statSync(p).isDirectory()) yield* files(p);
    else if (p.endsWith('.js')) yield p;
  }
}

let found = 0;
for (const file of files(root)) {
  const text = readFileSync(file, 'utf8');
  for (const [label, patterns] of rules) {
    for (const pattern of patterns) {
      const re = new RegExp(pattern.source, 'g');
      for (let m; (m = re.exec(text)); ) {
        found++;
        console.error(`[${label}] ${file}: ${text.slice(Math.max(0, m.index - 80), m.index + 40).replace(/\s+/g, ' ')}`);
      }
    }
  }
}
if (found > 0) {
  console.error(`검수에서 반려될 코드 ${found}건(metro.config.js의 토스 빌드 대체 모듈 참고).`);
  process.exit(1);
}
console.log('eval·외부 코드·토스 밖 이동 없음');
