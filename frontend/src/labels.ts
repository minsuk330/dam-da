import type { Schemas } from '@/api/client'

type Turn = Schemas['UserTurnResponse']
type Detail = Schemas['ConversationDetailResponse']

export const inputPathLabel: Record<Detail['inputPath'], string> = {
  connector: 'Claude 커넥터',
  share_link: '공유 링크',
  paste: '붙여넣기',
}

export const fidelityLabel: Record<Detail['fidelity'], string> = {
  verbatim: '원문',
  model_transcribed: '모델이 옮겨 적음',
}

export const intentLabel: Record<Turn['intent'], string> = {
  info_request: '질문',
  rephrase_request: '다시 설명 요청',
  understanding_check: '이해 확인',
  restatement: '내 말로 정리',
  challenge: '반론',
  meta: '대화 진행',
}

/** 대화 중 AI가 한 판정. 사용자 답변 판정이 아니다. */
export const aiVerdictLabel: Record<Turn['aiVerdict'], string | null> = {
  confirmed: '맞음',
  partial: '일부 맞음',
  corrected: '교정됨',
  not_applicable: null,
}

export const factKindLabel: Record<Schemas['KeyPointResponse']['kind'], string | null> = {
  fact: null,
  warning: '주의',
  practice: '실습 팁',
}

export const sessionStatusLabel: Record<Schemas['LearningSessionSummary']['status'], string> = {
  RECEIVED: '받음',
  REVIEWING: '검수 중',
  AWAITING_CONFIRMATION: '확인 필요',
  CONFIRMED: '확인 완료',
  QUESTIONS_READY: '문제 준비됨',
  IN_PROGRESS: '학습 중',
}

export const itemKindLabel: Record<Schemas['Item']['kind'], string> = {
  FACT: '핵심 내용',
  WARNING: '주의',
  PRACTICE: '실습 팁',
  CONFUSION: '헷갈렸던 점',
}

/** Jev가 복습 단위를 검수한 결과. 판정만 보여주고 제외 여부는 사용자가 정한다. */
export const unitVerdictLabel: Record<Schemas['Unit']['verdict'], string> = {
  PENDING: '검수 중',
  APPROVED: '검수 통과',
  HELD: '확인 필요',
  REJECTED: '빼기 권장',
  UNAVAILABLE: '검수 못 함',
}

export const questionTypeLabel: Record<Schemas['QuestionView']['type'], string> = {
  MULTIPLE_CHOICE: '객관식',
  SHORT_ANSWER: '단답',
  ESSAY: '서술',
  ERROR_FINDING: '오류 찾기',
  CASE_JUDGMENT: '사례 판단',
  CASE_APPLICATION: '사례 적용',
}

export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString('ko-KR', { dateStyle: 'medium', timeStyle: 'short' })
}
