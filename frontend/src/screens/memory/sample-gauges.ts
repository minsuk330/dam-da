import type { Schemas } from '@/api/client'

type Session = Schemas['LearningSessionSummary']

// 기억 게이지 API(이슈 #21) 전까지 쓰는 예시 값. API가 생기면 이 파일을 지우고 실제 R로 바꾼다.
// 확인을 마치기 전 세션은 초기 평가가 없으므로 실제와 같이 "아직 확인 전"(null)으로 둔다.
const CONFIRMED_OR_LATER: Session['status'][] = ['CONFIRMED', 'QUESTIONS_READY', 'IN_PROGRESS']
const SAMPLE_VALUES = [0.86, 0.72, 0.58, 0.91, 0.44]

export function sampleRetrievability(session: Session): number | null {
  if (!CONFIRMED_OR_LATER.includes(session.status)) return null
  return SAMPLE_VALUES[session.id % SAMPLE_VALUES.length]
}

/** 확인된 세션들의 예시 R 평균. 확인된 세션이 없으면 null. */
export function sampleAverage(sessions: Session[]): number | null {
  const values = sessions.map(sampleRetrievability).filter((v): v is number => v !== null)
  return values.length === 0 ? null : values.reduce((sum, v) => sum + v, 0) / values.length
}
