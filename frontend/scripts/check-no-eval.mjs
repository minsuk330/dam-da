// 토스 인앱 번들 검사(#149): 앱인토스 검수는 eval·new Function처럼 외부 코드를 실행할 수 있는 코드가 있으면 반려한다.
// dist-toss의 JS에 그런 호출이 하나라도 있으면 실패한다. `npm run toss:build`가 .ait를 만들기 전에 부른다.
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';

const root = process.argv[2] ?? 'dist-toss';
const patterns = [/(?<![\w$.])eval\s*\(/, /new\s+Function\s*\(/, /(?<![\w$.])Function\s*\(\s*["'`]/, /set(Timeout|Interval)\s*\(\s*["'`]/];

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
  for (const pattern of patterns) {
    const re = new RegExp(pattern.source, 'g');
    for (let m; (m = re.exec(text)); ) {
      found++;
      console.error(`${file}: ${text.slice(Math.max(0, m.index - 80), m.index + 40).replace(/\s+/g, ' ')}`);
    }
  }
}
if (found > 0) {
  console.error(`eval/new Function 호출 ${found}건. 앱인토스 검수에서 반려된다(metro.config.js의 토스 빌드 대체 모듈 참고).`);
  process.exit(1);
}
console.log('eval/new Function 없음');
