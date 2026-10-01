// 홈의 예시 값. 매일 학습 큐(이슈 #22)와 연속 학습 일수(이슈 #23) API가 생기면 이 파일을 지우고 API로 바꾼다.
// 기억 게이지 예시는 기억 탭과 같은 값을 쓴다(screens/memory/sample-gauges.ts).
import { sampleMinutes, sampleQuestions } from '@/screens/review/sample-questions'

/** 오늘의 학습: 복습할 항목과 새로 배울 항목은 따로 편성한다(스펙 §6.4.4). */
export const sampleToday = {
  reviewCount: sampleQuestions.length,
  newCount: 2,
  minutes: sampleMinutes,
}

/** 연속 학습 일수(스펙 §7.7). `studiedToday`가 false면 오늘 학습하면 하루 늘어난다. */
export const sampleStreak = {
  days: 4,
  studiedToday: false,
}
