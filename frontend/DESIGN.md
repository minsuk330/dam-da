---
version: alpha
name: AI Learning Companion
description: 매일 짧게 복습하는 학습 앱. 맑은 블루와 라벤더 면, 알약형 컨트롤, 큰 라운드. 레퍼런스(Fluentify 시안)에서 추출.
colors:
  canvas: "#F3F6FF"
  surface: "#FFFFFF"
  surface-soft: "#EEF2FF"
  outline: "#E2E7FA"
  ink: "#12141F"
  ink-secondary: "#3D4A6B"
  ink-muted: "#646B88"
  primary: "#2E5BE8"
  primary-ink: "#2448C8"
  primary-pressed: "#2149CF"
  on-primary: "#FFFFFF"
  primary-tint: "#D6E1FF"
  lavender: "#BDCDFF"
  lavender-deep: "#A9BDFF"
  field: "#DCE4FC"
  field-placeholder: "#59607A"
  inverse: "#0B0C12"
  on-inverse: "#FFFFFF"
  success: "#16A06A"
  success-tint: "#D9F4E8"
  success-ink: "#0B6B46"
  danger: "#E5484D"
  danger-tint: "#FFE3E3"
  danger-ink: "#A8242B"
  warning: "#F0A500"
  warning-tint: "#FFF0CC"
  warning-ink: "#7A4D00"
  glass: "#DCE4FCB8"
  glass-item: "#CED6EB"
typography:
  display:
    fontFamily: SUIT
    fontSize: 28px
    fontWeight: 700
    lineHeight: 1.3
  title:
    fontFamily: SUIT
    fontSize: 22px
    fontWeight: 700
    lineHeight: 1.35
  question:
    fontFamily: SUIT
    fontSize: 22px
    fontWeight: 600
    lineHeight: 1.4
  headline:
    fontFamily: SUIT
    fontSize: 17px
    fontWeight: 600
    lineHeight: 1.4
  body:
    fontFamily: SUIT
    fontSize: 16px
    fontWeight: 400
    lineHeight: 1.55
  subhead:
    fontFamily: SUIT
    fontSize: 14px
    fontWeight: 500
    lineHeight: 1.45
  caption:
    fontFamily: SUIT
    fontSize: 12px
    fontWeight: 500
    lineHeight: 1.4
  stat:
    fontFamily: SUIT
    fontSize: 26px
    fontWeight: 600
    lineHeight: 1.1
rounded:
  sm: 12px
  md: 20px
  lg: 28px
  xl: 32px
  full: 9999px
spacing:
  xs: 4px
  sm: 8px
  md: 12px
  lg: 16px
  xl: 20px
  2xl: 24px
  3xl: 32px
components:
  screen:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
  caption-text:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink-muted}"
    typography: "{typography.caption}"
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.headline}"
    rounded: "{rounded.full}"
    height: 56px
  button-primary-pressed:
    backgroundColor: "{colors.primary-pressed}"
    textColor: "{colors.on-primary}"
    rounded: "{rounded.full}"
  button-secondary:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    typography: "{typography.headline}"
    rounded: "{rounded.full}"
    height: 56px
  icon-button:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.full}"
    size: 48px
  icon-button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    rounded: "{rounded.full}"
    size: 44px
  answer-option:
    backgroundColor: "{colors.surface-soft}"
    textColor: "{colors.ink}"
    typography: "{typography.headline}"
    rounded: "{rounded.full}"
    height: 56px
  answer-option-selected:
    backgroundColor: "{colors.lavender}"
    textColor: "{colors.ink}"
    typography: "{typography.headline}"
    rounded: "{rounded.full}"
  answer-option-outline:
    backgroundColor: "{colors.outline}"
    width: 1px
  answer-option-correct:
    backgroundColor: "{colors.success-tint}"
    textColor: "{colors.success-ink}"
    typography: "{typography.headline}"
    rounded: "{rounded.full}"
  answer-option-wrong:
    backgroundColor: "{colors.danger-tint}"
    textColor: "{colors.danger-ink}"
    typography: "{typography.headline}"
    rounded: "{rounded.full}"
  state-ring-correct:
    backgroundColor: "{colors.success}"
    width: 2px
  state-ring-wrong:
    backgroundColor: "{colors.danger}"
    width: 2px
  feedback-correct:
    backgroundColor: "{colors.success-tint}"
    textColor: "{colors.success-ink}"
    typography: "{typography.subhead}"
    rounded: "{rounded.md}"
    padding: 16px
  feedback-wrong:
    backgroundColor: "{colors.danger-tint}"
    textColor: "{colors.danger-ink}"
    typography: "{typography.subhead}"
    rounded: "{rounded.md}"
    padding: 16px
  chip-warning:
    backgroundColor: "{colors.warning-tint}"
    textColor: "{colors.warning-ink}"
    typography: "{typography.caption}"
    rounded: "{rounded.full}"
  warning-mark:
    backgroundColor: "{colors.warning}"
    size: 8px
  chip-status:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.caption}"
    rounded: "{rounded.full}"
  chip-soft:
    backgroundColor: "{colors.primary-tint}"
    textColor: "{colors.primary-ink}"
    typography: "{typography.caption}"
    rounded: "{rounded.full}"
  card:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.lg}"
    padding: 20px
  card-lavender:
    backgroundColor: "{colors.lavender-deep}"
    textColor: "{colors.ink-secondary}"
    rounded: "{rounded.lg}"
    padding: 20px
  card-hero:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    rounded: "{rounded.xl}"
    padding: 24px
  field:
    backgroundColor: "{colors.field}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.md}"
  field-placeholder:
    backgroundColor: "{colors.field}"
    textColor: "{colors.field-placeholder}"
    typography: "{typography.body}"
  gauge:
    backgroundColor: "{colors.primary-tint}"
    rounded: "{rounded.full}"
    height: 8px
  gauge-large:
    backgroundColor: "{colors.primary-tint}"
    rounded: "{rounded.full}"
    height: 12px
  gauge-fill:
    backgroundColor: "{colors.primary}"
    rounded: "{rounded.full}"
  gauge-unchecked:
    backgroundColor: "{colors.outline}"
    rounded: "{rounded.full}"
  tab-bar:
    backgroundColor: "{colors.glass}"
    rounded: "{rounded.full}"
    padding: 8px
  tab-item:
    backgroundColor: "{colors.glass-item}"
    textColor: "{colors.ink-secondary}"
    rounded: "{rounded.full}"
    size: 56px
  tab-active:
    backgroundColor: "{colors.inverse}"
    textColor: "{colors.on-inverse}"
    rounded: "{rounded.full}"
    size: 56px
---

# AI Learning Companion 디자인 시스템

> 이 파일이 UI의 기준이다. 토큰 값은 위 YAML이 정본이고, 앱의 `theme/`는 여기서 만든다.
> 값은 사용자가 준 레퍼런스(Fluentify 학습 앱 시안, 3화면)에서 픽셀 샘플링으로 뽑고, 글자 대비가 WCAG AA(4.5:1)에 못 미치는 보조 텍스트만 어둡게 조정했다.

## Overview

AI와 나눈 대화에서 배운 것을 매일 짧게 복습하는 학습 앱. 톤은 **맑고 차분한 블루**에 **라벤더 면**을 더해 부드럽게.
모든 컨트롤은 알약형, 면은 큰 라운드. 정보 카드는 2열 벤토 배치로 한눈에 보이고, 문제 화면은 한 화면에 한 문제만 가운데 정렬로 보여준다.
기준 화면은 390×844 휴대폰. 웹 시연에서도 이 폭의 휴대폰 영역 안에 그린다.

## Colors

- **배경 `canvas` (아주 옅은 블루)** 위에 **흰 카드 `surface`**. 선택지·보조 면은 **`surface-soft`**.
- **Royal Blue `primary`**: 주 버튼, 상태 칩, 강조 카드(hero), 보내기·이동 원형 버튼. 화면당 주 버튼은 하나.
- **`lavender` / `lavender-deep`**: 선택된 답, 강조하지만 덜 시끄러운 정보 카드. 위에 올리는 글자는 `ink`나 `ink-secondary`만.
- **`primary-tint`**: 옅은 상태 칩 배경. 글자는 `primary-ink`(primary보다 진한 블루, 대비 확보용).
- **`field`**: 입력창 배경. placeholder는 `field-placeholder`.
- **텍스트**: `ink`(거의 검정) → `ink-secondary`(블루그레이) → `ink-muted`. 레퍼런스의 연회색 보조 텍스트(#888DA0)는 대비가 낮아 `ink-muted`로 대체했다.
- **`inverse`**: 탭바의 활성 탭처럼 아주 작은 면에만.
- **상태색** (레퍼런스에 없어 블루 팔레트에 맞춰 추가): 정답 `success`(그린), 오답 `danger`(레드), 주의 `warning`(앰버).
  - 글자는 항상 옅은 면(`*-tint`) 위의 진한 글자(`*-ink`)로 쓴다. 진한 상태색 위에 흰 글자를 올리지 않는다(대비 부족).
  - 진한 상태색(`success`/`danger`/`warning`)은 테두리·아이콘·점 같은 그래픽에만 쓴다.
  - 색만으로 의미를 전달하지 않는다. 정답/오답은 아이콘(✓/✕ 모양의 아이콘 세트 아이콘)과 글자를 함께 쓴다.
  - 오답은 벌주는 느낌이 아니라 바로 설명으로 이어지는 상태다. 빨간 면을 크게 쓰지 않는다.

## Typography

- 글꼴 **SUIT** 하나. 레퍼런스의 기하학적 산세리프와 비슷한 한국어 글꼴. 굵기 파일을 각각 로드하고 굵기는 fontFamily 이름으로 지정한다.
- `display` 화면 큰 제목(왼쪽 정렬, 2줄까지), `title` 카드 제목, `question` 문제 문장(가운데 정렬), `headline` 버튼·선택지·섹션 제목, `body` 본문, `subhead` 보조 설명, `caption` 칩·메타, `stat` 카드 안 숫자(옆에 `caption` 단위).

## Layout

- 화면 좌우 여백 20px(`xl`). 카드 사이 12px(`md`), 카드 안 여백 20~24px.
- 홈: 헤더(원형 메뉴 버튼 · 서비스명 · 알림 · 프로필) → 큰 제목 + 상태 칩 → 2열 통계 카드 → 전체 폭 카드 목록.
- 문제 화면: 상단 가운데 남은 시간 칩 → 가운데 정렬 질문 → 2×2 알약형 선택지 → 하단 전체 폭 주 버튼.
- 간격은 위계를 가진다: 행 사이 < 카드 사이 < 섹션 사이. 모든 곳을 같은 16px로 두지 않는다.

## Elevation & Depth

- 그림자를 거의 쓰지 않는다. 깊이는 `canvas` / `surface` / `lavender` / `primary` 면의 대비로 만든다.
- 테두리는 선택지처럼 배경과 거의 같은 면을 구분할 때만 1px `outline`.

## Shapes

- 버튼·선택지·칩·원형 버튼은 완전 둥글게(`full`).
- 카드 28px(`lg`), 강조 카드 32px(`xl`), 입력창 20px(`md`), 작은 요소 12px(`sm`).
- 원형 아이콘 버튼 48px(흰색), 원형 실행 버튼 44px(primary).

## Components

- **주 버튼**: primary, 흰 글자, 높이 56, 전체 폭, 알약형. 화면 하단에 하나. 누르면 `primary-pressed`로 어두워진다(크기 변화 없음).
- **보조 버튼**: 흰 바탕, ink 글자, 알약형.
- **선택지**: `surface-soft` + 1px `outline`, 알약형, 높이 56. 선택되면 `lavender`로 채우고 테두리를 없앤다.
  - 채점 후: 정답 선택지는 `answer-option-correct` + 2px `success` 테두리 + ✓ 아이콘, 고른 오답은 `answer-option-wrong` + 2px `danger` 테두리 + ✕ 아이콘. 나머지는 그대로 둔다.
- **채점 피드백**: 선택지 아래, 주 버튼 위에 `feedback-correct` / `feedback-wrong` 상자. 첫 줄은 결과(맞았어요 / 아쉬워요), 다음 줄부터 설명과 근거 발화. 주 버튼은 "다음"으로 바뀐다.
- **주의 표시**: warning 핵심 내용에는 `chip-warning`(주의). 목록에서는 8px `warning` 점.
- **상태 칩**: `chip-status`(채움, 진행 중 같은 현재 상태) / `chip-soft`(옅음, 보조 상태).
- **통계 카드**: 왼쪽 위 원형 아이콘, 오른쪽에 `stat` 숫자 + `caption` 단위, 아래에 `subhead` 설명. 흰 카드 또는 라벤더 카드.
- **강조 카드**: primary 바탕 흰 글자, 오른쪽 아래 원형 이동 버튼. 화면당 하나.
- **입력창**: `field` 바탕, 오른쪽 끝 원형 primary 보내기 버튼.
- **탭바** (레퍼런스의 떠 있는 유리 탭바, 2026-10-01 사용자 결정으로 채택): 화면 아래 가운데에 떠 있는 알약(`tab-bar`). 바탕은 반투명 `glass` + 뒤 배경 흐림(웹 `backdrop-filter`, 흐림이 안 되는 곳은 반투명 면만).
  - 탭마다 56px 원(`tab-item`, 탭바보다 한 톤 진한 불투명 `glass-item` 면 + `ink-secondary` 아이콘). 활성 탭은 `inverse` 원 + `on-inverse` 아이콘(`tab-active`).
  - 아이콘만 보이고 글자 라벨은 없다. 대신 접근성 라벨을 반드시 단다.
  - 그림자는 쓰지 않는다. 떠 있어도 깊이는 흐림과 반투명으로 만든다.
  - 탭바가 내용 위에 떠 있으므로, 탭 화면의 스크롤 내용은 아래에 탭바 높이만큼 여백을 둔다.
- **기억 게이지** (스펙 §6.4.3, 레퍼런스에 없어 추가): 지금 떠올릴 확률 R을 보여주는 가로 막대.
  - 트랙 `primary-tint`, 채움 `primary`(`gauge-fill`), 알약형. 기본 높이 8(`gauge`, 목록 행·복습 단위), 큰 높이 12(`gauge-large`, 세션 요약·완료 화면).
  - 막대 위에 라벨과 퍼센트를 글자로 함께 쓴다("지금 기억할 확률 72%"). 막대 길이만으로 값을 전달하지 않는다. 글자는 막대가 아니라 카드 위에 있으므로 `ThemedText`로 쓴다: 기본은 `caption`·`ink-secondary`, 큰 게이지는 `subhead`·`ink`, 확인 전은 `caption`·`ink-muted`.
  - 값이 낮다고 색을 바꾸지 않는다. 약한 항목은 "가장 약한 항목" 같은 글자로 알린다.
  - 아직 등급이 없는 항목은 0%가 아니다. 채움 없이 `gauge-unchecked` 트랙(`outline`)과 "아직 확인 전" 글자로 구분한다.
  - 진행 막대(풀이 진행도)가 필요하면 같은 토큰을 쓴다.

## Do's and Don'ts

- Do: 한 화면에 한 가지 일. 문제 화면은 문제 하나.
- Do: 숫자는 크게(`stat`), 단위와 설명은 작게.
- Do: 원문 근거(발화 번호), "대화 중 AI 판정" 같은 출처를 라벨로 밝힌다.
- Don't (레퍼런스에서 가져오지 않는 것): 화면 위쪽 블루 그라데이션 글로우, AI 오브 같은 장식 블롭, 빗금 패턴 카드. 반투명 유리는 탭바에만 쓰고 카드·버튼에는 쓰지 않는다.
- Don't: 토큰에 없는 색·크기·라운드. 하드코딩 hex는 `theme/` 밖에 두지 않는다.
- Don't: 이모지를 아이콘으로 쓰기. 아이콘은 한 가지 아이콘 세트만.
- Don't: 모든 섹션을 카드로 감싸기, 카드 안 카드.
- Don't: 모든 화면 진입 시 순차 등장 애니메이션, 모든 터치에 크기 줄이기 효과. 버튼은 살짝 어둡게, 행은 배경색 변화로.
- Don't: 상태가 바뀔 때마다 화면 전체 스피너. 불러오는 중·비어 있음·오류·정상 네 상태를 구분한다.
