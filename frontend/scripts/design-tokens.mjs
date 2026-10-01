// DESIGN.md의 YAML 토큰을 React Native용 src/theme/tokens.ts로 옮긴다.
// DESIGN.md가 정본이다. tokens.ts는 손으로 고치지 않고 `npm run design:tokens`로 다시 만든다.
import { readFileSync, writeFileSync } from 'node:fs';
import { parse } from 'yaml';

const SOURCE = new URL('../DESIGN.md', import.meta.url);
const TARGET = new URL('../src/theme/tokens.ts', import.meta.url);

// 굵기별 정적 폰트 파일(assets/fonts)의 family 이름. RN은 fontWeight로 굵기를 고르지 못하므로 family로 지정한다.
const FONT_FILES = { 400: 'Regular', 500: 'Medium', 600: 'SemiBold', 700: 'Bold' };

const markdown = readFileSync(SOURCE, 'utf8');
const frontMatter = markdown.match(/^---\n([\s\S]*?)\n---/);
if (!frontMatter) throw new Error('DESIGN.md에 YAML front matter가 없다');
const tokens = parse(frontMatter[1]);

const px = (value) => {
  if (typeof value === 'number') return value;
  const match = String(value).match(/^(-?[\d.]+)px$/);
  if (!match) throw new Error(`px 값이 아니다: ${value}`);
  return Number(match[1]);
};

const camel = (name) => name.replace(/-([a-z0-9])/g, (_, c) => c.toUpperCase());
const camelKeys = (object, map = (v) => v) =>
  Object.fromEntries(Object.entries(object).map(([k, v]) => [camel(k), map(v)]));

const textStyle = (t) => {
  const file = FONT_FILES[Number(t.fontWeight)];
  if (!file) throw new Error(`지원하지 않는 굵기: ${t.fontWeight} (assets/fonts에 파일이 있는 굵기만 쓴다)`);
  const fontSize = px(t.fontSize);
  const lineHeight = typeof t.lineHeight === 'number' ? Math.round(fontSize * t.lineHeight) : px(t.lineHeight);
  return { fontFamily: `${t.fontFamily}-${file}`, fontSize, lineHeight };
};

const colors = camelKeys(tokens.colors);
const spacing = camelKeys(tokens.spacing, px);
const radius = camelKeys(tokens.rounded, px);
const typography = camelKeys(tokens.typography, textStyle);

const resolve = (value) => {
  const ref = typeof value === 'string' && value.match(/^\{(\w+)\.([\w-]+)\}$/);
  if (!ref) return /px$/.test(String(value)) ? px(value) : value;
  const [, group, name] = ref;
  const table = { colors, rounded: radius, spacing, typography }[group];
  const resolved = table?.[camel(name)];
  if (resolved === undefined) throw new Error(`깨진 참조: ${value}`);
  return resolved;
};
const components = camelKeys(tokens.components, (c) => camelKeys(c, resolve));

const fontFamilies = [...new Set(Object.values(typography).map((t) => t.fontFamily))].sort();

const out = `// DESIGN.md에서 생성된 파일. 손으로 고치지 않는다 — DESIGN.md를 고치고 \`npm run design:tokens\`.
export const colors = ${JSON.stringify(colors, null, 2)} as const;

export const spacing = ${JSON.stringify(spacing, null, 2)} as const;

export const radius = ${JSON.stringify(radius, null, 2)} as const;

export const typography = ${JSON.stringify(typography, null, 2)} as const;

export const components = ${JSON.stringify(components, null, 2)} as const;

/** 로드해야 하는 폰트 family 이름 (assets/fonts/<이름>.otf). */
export const fontFamilies = ${JSON.stringify(fontFamilies)} as const;
`;

writeFileSync(TARGET, out);
console.log(`src/theme/tokens.ts ← DESIGN.md (${Object.keys(colors).length} colors, ${Object.keys(typography).length} text styles, ${Object.keys(components).length} components)`);
