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

/** 다음 복습일처럼 날짜만 필요한 곳. 서버 시각(시간 이동)과 기기 시각이 다를 수 있어 "며칠 뒤" 대신 날짜로 쓴다. */
export function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('ko-KR', { month: 'long', day: 'numeric', weekday: 'short' })
}

/**
 * 다음 복습일 한 줄. 서버의 오늘(`DailyView.date`, 시간 이동 반영)이 그날이거나 지났으면 복습할 차례라고 쓴다.
 * 서버의 오늘을 모르면(아직 못 불러왔으면) 날짜만 쓴다. 기기 날짜는 시간 이동과 달라 비교에 쓰지 않는다.
 */
export function nextReviewLabel(iso: string, serverToday: string | undefined): string {
  const date = new Date(iso)
  const day = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
  if (serverToday !== undefined && day <= serverToday) return '복습할 때가 됐어요'
  return `다음 복습 ${formatDate(iso)}`
}

/** 지금 쓰는 기억 모델과 개인화 진행도 한 줄. 불러오기 전이면 화면 설명을 보여준다. */
export function memoryModelStatus(model: Schemas['MemoryModel'] | undefined): string {
  if (!model) return '망각 곡선과 기억 유지 기간'
  if (model.status === 'PERSONALIZED') return `개인 모델 v${model.parametersVersion} 적용 중`
  const { gradedReviews, requiredReviews } = model.progress
  return `기본 모델 · 복습 기록 ${gradedReviews.toLocaleString()} / ${requiredReviews.toLocaleString()}`
}

/** 매일 학습 설정 한 줄. 서버 시각은 "07:30:00"으로 올 수 있어 "HH:mm"만 쓴다. */
export function dailySettingsStatus(settings: Schemas['Settings'] | undefined): string {
  if (!settings) return '하루 학습 시간과 알림 시각'
  const notify = settings.notifyAt === null ? '알림 꺼짐' : `${settings.notifyAt.slice(0, 5)} 알림`
  return `하루 ${settings.budgetMinutes}분 · ${notify}`
}
