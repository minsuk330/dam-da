import { useMutation, useQueryClient } from '@tanstack/react-query'

import { api, unwrap, type Schemas } from './client'
import { mockEnabled, mockResponse } from './mock'
import { mockPracticeApi } from './mock-practice'

// 풀이 API. 다음 문제(`next`)는 GET이지만 답한 뒤 부르면 다음 문제를 제시(기록)하므로,
// 캐시 쿼리로 두지 않고 화면이 순서대로 부른다(포커스 재조회로 문제가 넘어가지 않게).

export type Presentation = Schemas['PresentationView']
export type Attempt = Schemas['AttemptView']

// 계약이 null 가능한 $ref 필드를 `{"type": "null", "$ref": ...}`로 내보내 생성 타입에서 null이 빠진다(백엔드 이슈 #73).
// 고쳐질 때까지 그 필드만 null을 더한다.
export type Feedback = Omit<Schemas['FeedbackView'], 'prerequisite'> & {
  prerequisite: Schemas['Prerequisite'] | null
}
export type Next = Omit<Schemas['Next'], 'presentation'> & { presentation: Presentation | null }
export type Submission = Schemas['Submission']
export type SelfAssessment = NonNullable<Submission['selfAssessment']>

/** mock 함수가 던진 ApiError도 실제 요청처럼 늦게 거절한다. */
async function mockCall<T>(run: () => T): Promise<T> {
  return mockResponse(null).then(() => structuredClone(run()))
}

/** 첫 학습 풀이를 시작한다. 이미 시작했으면 그 풀이를 이어서 연다. */
export function useStartFirstStudy(sessionId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async () =>
      mockEnabled
        ? mockCall(() => mockPracticeApi.startFirstStudy(sessionId))
        : unwrap(await api.POST('/api/sessions/{sessionId}/first-study/practice', { params: { path: { sessionId } } })),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['learning-sessions'] }),
  })
}

/** 지금 풀 문제. 아직 답하지 않았으면 같은 문제를, 답했으면 다음 문제를 준다. 다 풀었으면 `done`. */
export async function fetchNext(practiceId: number): Promise<Next> {
  return mockEnabled
    ? mockCall(() => mockPracticeApi.next(practiceId))
    : unwrap(await api.GET('/api/practice/{practiceId}/next', { params: { path: { practiceId } } }))
}

/** 답을 내고 판정 결과를 받는다. 객관식은 `choiceIndex`, 그 밖에는 `answer`. */
export async function submitAttempt(presentationId: number, body: Submission): Promise<Attempt> {
  return mockEnabled
    ? mockCall(() => mockPracticeApi.submit(presentationId, body))
    : unwrap(
        await api.POST('/api/practice/presentations/{presentationId}/attempts', {
          params: { path: { presentationId } },
          body,
        }),
      )
}

/**
 * 마지막 시도로 다음 행동(힌트·개념 설명·다음 문제 등)을 서버가 정해 실행한다.
 * 힌트·설명 노출 기록(`aids`)과 같은 날 확인 문제 편성도 서버가 한다.
 */
export async function decideFeedback(presentationId: number): Promise<Feedback> {
  return mockEnabled
    ? mockCall(() => mockPracticeApi.feedback(presentationId))
    : unwrap(
        await api.POST('/api/practice/presentations/{presentationId}/feedback', { params: { path: { presentationId } } }),
      )
}
