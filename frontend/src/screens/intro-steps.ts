import type { IconName } from '@/components/icon';
import { inToss } from '@/toss';

/**
 * 서비스가 하는 일 세 줄. 웹 로그인 화면과 홈의 첫 사용자 화면이 함께 쓴다.
 * 토스 인앱(#149)은 커넥터(구글·카카오 계정)를 쓸 수 없어 공유 링크·붙여넣기로 안내한다.
 */
export const INTRO_STEPS: { icon: IconName; title: string; detail: string }[] = [
  inToss
    ? { icon: 'message-circle', title: 'AI와 나눈 대화를 담아요', detail: '공유 링크나 붙여넣기로 한 번에 담아요' }
    : { icon: 'message-circle', title: 'Claude와 나눈 대화를 보내요', detail: '커넥터나 공유 링크로 한 번에 담아요' },
  { icon: 'check-square', title: '배운 내용을 문제로 확인해요', detail: '대화 속 내 질문에서 나온 것만 물어봐요' },
  { icon: 'calendar', title: '잊을 때쯤 매일 몇 분 복습해요', detail: '기억 상태를 계산해 복습할 때를 골라요' },
];

/** 생성형 AI 사전 고지(앱인토스 서비스 오픈 정책 2-4, #149). 처음 쓰는 시점(웹 로그인 화면, 첫 사용자 홈, 첫 저장)에 보인다. */
export const AI_NOTICE = '배울 내용 정리, 문제·힌트·설명, 답 채점은 생성형 AI가 해요. AI 결과는 틀릴 수 있어요.';
