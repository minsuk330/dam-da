---
version: alpha
name: 담다
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
  stage: "#D4D7DE"
  success: "#16A06A"
  success-tint: "#D9F4E8"
  success-ink: "#0B6B46"
  danger: "#E5484D"
  danger-tint: "#FFE3E3"
  danger-ink: "#A8242B"
  warning: "#F0A500"
  warning-tint: "#FFF0CC"
  warning-ink: "#7A4D00"
  warning-fill: "#B87800"
  glass: "#DCE4FCB8"
  glass-item: "#CED6EB"
  kakao: "#FEE500"
  kakao-pressed: "#E5CE00"
  kakao-ink: "#000000D9"
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
  2xs: 2px
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
  button-danger:
    backgroundColor: "{colors.danger-tint}"
    textColor: "{colors.danger-ink}"
    typography: "{typography.headline}"
    rounded: "{rounded.full}"
    height: 56px
  button-kakao:
    backgroundColor: "{colors.kakao}"
    textColor: "{colors.kakao-ink}"
    typography: "{typography.headline}"
    rounded: "{rounded.full}"
    height: 56px
  button-kakao-pressed:
    backgroundColor: "{colors.kakao-pressed}"
    textColor: "{colors.kakao-ink}"
    rounded: "{rounded.full}"
  button-google:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    typography: "{typography.headline}"
    rounded: "{rounded.full}"
    height: 56px
  button-google-outline:
    backgroundColor: "{colors.outline}"
    width: 1px
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
  icon-small:
    size: 16px
  icon:
    size: 20px
  icon-large:
    size: 24px
  icon-xl:
    size: 32px
  icon-circle:
    backgroundColor: "{colors.primary-tint}"
    textColor: "{colors.primary-ink}"
    rounded: "{rounded.full}"
    size: 44px
  done-mark:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    rounded: "{rounded.full}"
    size: 72px
  celebration:
    size: 390px
  phone-stage:
    backgroundColor: "{colors.stage}"
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
    height: 156px
  card-hero-pressed:
    backgroundColor: "{colors.primary-pressed}"
    textColor: "{colors.on-primary}"
    rounded: "{rounded.xl}"
  card-stat:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.lg}"
    padding: 20px
    height: 152px
  card-pressed:
    backgroundColor: "{colors.surface-soft}"
    textColor: "{colors.ink}"
    rounded: "{rounded.lg}"
  field:
    backgroundColor: "{colors.field}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.md}"
  field-placeholder:
    backgroundColor: "{colors.field}"
    textColor: "{colors.field-placeholder}"
    typography: "{typography.body}"
  text-area-compact:
    height: 96px
  text-area:
    height: 160px
  text-area-large:
    height: 220px
  gauge:
    backgroundColor: "{colors.primary-tint}"
    textColor: "{colors.primary-ink}"
    typography: "{typography.caption}"
    rounded: "{rounded.full}"
    height: 8px
  gauge-large:
    backgroundColor: "{colors.primary-tint}"
    rounded: "{rounded.full}"
    height: 12px
  gauge-fill:
    backgroundColor: "{colors.primary}"
    rounded: "{rounded.full}"
  gauge-review:
    backgroundColor: "{colors.warning-tint}"
    textColor: "{colors.warning-ink}"
    typography: "{typography.caption}"
    rounded: "{rounded.full}"
  gauge-review-fill:
    backgroundColor: "{colors.warning-fill}"
    rounded: "{rounded.full}"
  gauge-low:
    backgroundColor: "{colors.danger-tint}"
    textColor: "{colors.danger-ink}"
    typography: "{typography.caption}"
    rounded: "{rounded.full}"
  gauge-low-fill:
    backgroundColor: "{colors.danger}"
    rounded: "{rounded.full}"
  gauge-unchecked:
    backgroundColor: "{colors.outline}"
    rounded: "{rounded.full}"
  chart:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink-muted}"
    typography: "{typography.caption}"
    height: 168px
  chart-line:
    backgroundColor: "{colors.primary}"
    width: 2px
  chart-line-context:
    backgroundColor: "{colors.ink-muted}"
    width: 2px
  chart-grid:
    backgroundColor: "{colors.outline}"
    width: 1px
  chart-marker:
    backgroundColor: "{colors.primary}"
    size: 8px
  graph:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink-secondary}"
    typography: "{typography.caption}"
    rounded: "{rounded.lg}"
    height: 380px
  graph-edge:
    backgroundColor: "{colors.lavender}"
    width: 1px
  graph-node-field:
    size: 36px
  graph-node-subfield:
    size: 24px
  graph-node-session:
    size: 16px
  graph-node-unit:
    size: 10px
  graph-node-growth:
    size: 2px
  graph-node-unchecked:
    backgroundColor: "{colors.stage}"
  graph-node-selected:
    backgroundColor: "{colors.inverse}"
    width: 2px
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

# 담다 디자인 시스템

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
- **위험 버튼** (#148): 되돌릴 수 없는 일(회원 탈퇴)의 마지막 확인에만. `danger-tint` 면 + `danger-ink` 글자, 알약형. 진한 빨강 면에 흰 글자를 쓰지 않는다. 한 번 더 묻는 단계 없이 바로 보이지 않게, 앞 단계(보조 버튼)를 누른 뒤에 나타낸다.
- **소셜 로그인 버튼** (스펙 §7.9, 2026-10-02 사용자 결정: 구글·카카오만): 각 회사 브랜드 가이드를 따르고 높이·라운드만 우리 버튼과 맞춘다(56, 알약형).
  - 카카오: `button-kakao`(노랑 `kakao` + 85% 검정 `kakao-ink`) + 검정 말풍선 심볼. 눌리면 `button-kakao-pressed`.
  - 구글: `button-google`(흰 바탕 + 1px `button-google-outline`) + 4색 G 로고. 눌리면 `card-pressed` 면.
  - 로고는 왼쪽, 글자는 가운데. 로고 색은 브랜드 고정색이라 토큰이 아니라 로고 컴포넌트 안에 둔다.
  - 브랜드 버튼은 주 버튼(primary)이 아니다. 로그인 화면에는 primary 주 버튼을 두지 않는다.
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
  - 알약형. 기본 높이 8(`gauge`, 목록 행·복습 단위), 큰 높이 12(`gauge-large`, 세션 요약·완료 화면).
  - **세 단계 색** (2026-10-01 사용자 결정). 기준은 고정 %가 아니라 그 항목의 **목표 유지율**(기억 강도, 스펙 §6.4.7)이다. 매일 학습 큐가 "R이 목표 유지율 아래면 복습 대상"(§6.4.4)이므로 색의 뜻이 "복습할 때인가"와 같아진다. 목표 유지율을 모르면 0.9로 본다.
    | 단계 | 조건 | 트랙·라벨 칩 | 채움 | 라벨 |
    |---|---|---|---|---|
    | 잘 기억 | R ≥ 목표 | `gauge` (primary-tint / primary-ink) | `gauge-fill` (primary) | 잘 기억해요 |
    | 복습할 때 | 목표 − 0.15 ≤ R < 목표 | `gauge-review` (warning-tint / warning-ink) | `gauge-review-fill` (warning-fill) | 복습할 때예요 |
    | 많이 잊음 | R < 목표 − 0.15 | `gauge-low` (danger-tint / danger-ink) | `gauge-low-fill` (danger) | 많이 잊었어요 |
  - 그라데이션으로 섞지 않고 세 단계로 끊는다. 트랙도 단계 색의 옅은 면으로 바꾼다. 채움은 자기 트랙 위에서 3:1 이상이다(노랑은 `warning`이 1.6:1이라 더 진한 `warning-fill`을 쓴다).
  - 색만으로 뜻을 전하지 않는다. 퍼센트 옆에 단계 라벨을 트랙과 같은 색의 작은 칩으로 붙인다.
  - 게이지는 흰 카드(`card`) 위에 둔다. 라벤더·primary 면 위에서는 단계 색이 탁해진다. 빨강 단계도 벌주는 말이 아니라 담담하게("많이 잊었어요") 쓴다.
  - 막대 위에 라벨과 퍼센트를 글자로 함께 쓴다("지금 기억할 확률 72%"). 막대 길이만으로 값을 전달하지 않는다. 글자는 막대가 아니라 카드 위에 있으므로 `ThemedText`로 쓴다: 기본은 `caption`·`ink-secondary`, 큰 게이지는 `subhead`·`ink`, 확인 전은 `caption`·`ink-muted`.
  - 아직 등급이 없는 항목은 0%가 아니다. 채움 없이 `gauge-unchecked` 트랙(`outline`)과 "아직 확인 전" 글자로 구분한다.
  - 진행 막대(풀이 진행도·단계 표시)는 단계 색 없이 `gauge` 트랙 + `gauge-fill`만 쓴다. 진행은 좋고 나쁨이 아니다.
  - 채움은 처음 보이거나 값이 바뀔 때 0.6초 동안 지금 값까지 차오른다(ease-out). 움직임 줄이기가 켜져 있으면 바로 그린다. 시간은 `src/theme/index.ts`의 `motion`.
- **불러오는 중 (skeleton)**: 실제 레이아웃 자리에 `outline` 색 면(글자 줄·원형 아이콘·카드)을 두고 천천히 흐려졌다 돌아오게 한다(`Skeleton`, 목록은 `SkeletonList`). 화면 가운데 스피너 하나로 대신하지 않는다. 버튼 안 진행 표시는 스피너를 그대로 쓴다.

- **망각 곡선 차트** (스펙 §6.4.9, 내 기억 패턴 화면): x축 일수(0~30), y축 기억할 확률(0~100%) 선 그래프.
  - 흰 카드(`chart`) 위. 선은 2px(`chart-line`), 끝점에 8px 점(`chart-marker`)과 2px `surface` 테두리.
  - 비교 곡선(기본 모델)은 강조 하나·나머지 회색 원칙으로 `chart-line-context`(ink-muted) 2px. 색 대신 범례(짧은 선 표시 + 글자)와 끝 값 글자로 구분한다.
  - 격자는 1px `outline`(`chart-grid`), 실선. 목표 유지율 90% 선은 격자와 구분되게 `ink-muted` 1px 점선(점 간격 4)으로 긋고 글자 라벨을 붙인다. 글자는 선 색이 아니라 `ink-muted`/`ink-secondary`.
  - y축은 0%부터 시작한다. 축은 하나만 쓴다.
  - 차트 아래에 주요 일수(7·30일)의 값을 글자로 함께 쓴다. 선 모양만으로 값을 전달하지 않는다.
- **지식 그래프** (스펙 §7.10, 기억 탭 `목록 | 그래프`, 2026-10-02 사용자 결정: Quartz 그래프 UI): 분야 → 세부 분야 → 학습 → 주제를 점과 선으로 잇는 그래프.
  - 웹은 [Quartz](https://github.com/jackyzha0/quartz) v4 그래프 컴포넌트를 옮겨 쓴다(MIT, `src/screens/memory/graph-canvas.web.tsx`에 저작권 고지). 점을 끌어 옮기고, 휠·두 손가락으로 확대/이동한다(0.25~4배). 네이티브는 같은 힘으로 한 번 계산한 고정 배치를 SVG로 그린다.
  - 흰 카드(`graph`, 높이 380) 안에 그린다. 선은 1px `graph-edge`(lavender), 고른 점과 이어진 선은 `ink-muted`. 그림자·글로우는 쓰지 않는다.
  - 점 지름은 단계별 기본값(`graph-node-field` 36 / `graph-node-subfield` 24 / `graph-node-session` 16 / `graph-node-unit` 10)에 기억할 내용 수의 제곱근 × `graph-node-growth`(2)를 더한다.
  - 점 색은 기억 게이지 세 단계의 채움색(`gauge-fill` / `gauge-review-fill` / `gauge-low-fill`)이다. 확인된 항목이 없으면 `graph-node-unchecked`(회색). 고른 점은 2px `graph-node-selected` 테두리.
  - 점을 누르면(웹은 가리켜도) 그 점과 바로 이웃만 또렷하고 나머지 점·선은 20%로 흐려진다(0.2초 전환). 분야·세부 분야 이름은 늘 보이고, 학습·주제 이름은 고른 점의 이웃이거나 확대할수록 나타난다(`caption`).
  - 사용자가 끌거나 확대하기 전까지는 배치가 자리 잡는 동안 모든 점과 이름이 카드 안에 들어오도록 자동으로 맞춘다. 움직임 줄이기가 켜져 있으면 배치를 미리 끝까지 계산해 멈춘 상태로 그린다.
  - 색만으로 뜻을 전하지 않는다. 그래프 아래 범례(색 점 + 글자)와, 고른 점의 경로·이름·기억 게이지(라벨·퍼센트·단계 칩)를 흰 카드에 보여주고, 학습·주제면 "학습 열기" 보조 버튼을 둔다.
  - 캔버스 점은 화면 읽기 프로그램이 읽지 못하므로, 웹은 점마다 숨은 버튼(이름, 지금 기억할 확률 또는 아직 확인 전)을 함께 둔다.
- **눌림·비활성·제외 상태**: 버튼은 `primary-pressed`로 어둡게, 카드·행은 배경을 `card-pressed`(surface-soft)로, 강조 카드는 `card-hero-pressed`로 바꾼다. 투명도로 눌림을 표시하지 않는다. 비활성은 불투명도 0.4, 사용자가 뺀 항목은 0.6으로 흐리게 한다(이 두 값은 design.md 스키마에 투명도 토큰이 없어 `src/theme/index.ts`의 `opacity`에 둔다).
- **아이콘**: 크기는 `icon-small`(16) / `icon`(20) / `icon-large`(24) / `icon-xl`(32)만 쓴다. 아이콘을 담는 원은 `icon-circle`(44), 완료 표시는 `done-mark`(72).
- **축하 애니메이션** (2026-10-02 사용자 결정): 학습 완료·연속 학습 달성처럼 무언가를 이룬 순간에만 `assets/animations/confetti.lottie`를 한 번 재생한다. 색은 파일 원본 그대로 쓴다.
  - 화면 위쪽 `celebration`(390) 정사각 영역에 내용 위로 겹쳐 그리고, 터치를 막지 않는다. 가로로 긴 원본은 영역을 채우도록 잘라 맞춘다(cover).
  - 반복하지 않는다. 재생이 끝나면 지운다. 기기의 움직임 줄이기 설정이 켜져 있으면 재생하지 않는다.
- **다크 모드**: 지원하지 않는다(라이트 전용). 다크 토큰을 추가하기 전까지 OS 테마와 상관없이 라이트로 그린다.

## Do's and Don'ts

- Do: 한 화면에 한 가지 일. 문제 화면은 문제 하나.
- Do: 숫자는 크게(`stat`), 단위와 설명은 작게.
- Do: 원문 근거(발화 번호), "대화 중 AI 판정" 같은 출처를 라벨로 밝힌다.
- Don't (레퍼런스에서 가져오지 않는 것): 화면 위쪽 블루 그라데이션 글로우, AI 오브 같은 장식 블롭, 빗금 패턴 카드. 반투명 유리는 탭바에만 쓰고 카드·버튼에는 쓰지 않는다.
- Don't: 토큰에 없는 색·크기·라운드. 하드코딩 hex는 `theme/` 밖에 두지 않는다.
- Don't: 이모지를 아이콘으로 쓰기. 아이콘은 한 가지 아이콘 세트만.
- Don't: 모든 섹션을 카드로 감싸기, 카드 안 카드.
- Don't: 축하 애니메이션을 일상 화면(홈 진입, 문제 하나 맞힘)에 쓰기. 무언가를 이룬 순간에만.
- Don't: 모든 화면 진입 시 순차 등장 애니메이션, 모든 터치에 크기 줄이기 효과. 버튼은 살짝 어둡게, 행은 배경색 변화로.
- Don't: 상태가 바뀔 때마다 화면 전체 스피너. 불러오는 중·비어 있음·오류·정상 네 상태를 구분한다.
