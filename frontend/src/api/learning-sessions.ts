import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { api, unwrap, type Schemas } from './client'
import { mockEnabled, mockLearningSessions, mockResponse } from './mock'
import { mockSessionApi } from './mock-sessions'

type Detail = Schemas['LearningSessionDetail']
type Turn = Schemas['Turn']
type Goal = Schemas['GoalView']['goal']
type Strength = Schemas['Option']['strength']

/** mock 함수가 던진 ApiError도 실제 요청처럼 늦게 거절한다. */
async function mockCall<T>(run: () => T): Promise<T> {
  return mockResponse(null).then(run)
}

/** 학습 세션 목록. 최근 것부터. */
export function useLearningSessions() {
  return useQuery({
    queryKey: ['learning-sessions'],
    queryFn: async () =>
      mockEnabled ? mockResponse(mockLearningSessions) : unwrap(await api.GET('/api/learning-sessions')),
  })
}

const detailKey = (id: number) => ['learning-sessions', id] as const

/** 확인 화면이 읽는 세션 상세(발화·복습 단위·기억 항목·경고). */
export function useLearningSession(id: number) {
  return useQuery({
    queryKey: detailKey(id),
    queryFn: async () =>
      mockEnabled
        ? mockCall(() => structuredClone(mockSessionApi.detail(id)))
        : unwrap(await api.GET('/api/learning-sessions/{sessionId}', { params: { path: { sessionId: id } } })),
  })
}

type Edit =
  | { kind: 'unit'; unitId: number; excluded: boolean }
  | { kind: 'item'; itemId: number; excluded: boolean }
  | { kind: 'turn'; index: number; content: Pick<Turn, 'text' | 'intent' | 'aiVerdict' | 'correction'> }
  | { kind: 'insert'; afterIndex: number; content: Pick<Turn, 'text' | 'intent'>; sourceOf: number[] }
  | { kind: 'confirm' }

async function sendEdit(id: number, edit: Edit): Promise<Detail> {
  if (mockEnabled) {
    return mockCall(() => {
      switch (edit.kind) {
        case 'unit':
          return structuredClone(mockSessionApi.setUnitExcluded(id, edit.unitId, edit.excluded))
        case 'item':
          return structuredClone(mockSessionApi.setItemExcluded(id, edit.itemId, edit.excluded))
        case 'turn':
          return structuredClone(mockSessionApi.editTurn(id, edit.index, edit.content))
        case 'insert':
          return structuredClone(mockSessionApi.insertTurn(id, edit.afterIndex, edit.content, edit.sourceOf))
        case 'confirm':
          return structuredClone(mockSessionApi.confirm(id))
      }
    })
  }
  const sessionId = id
  switch (edit.kind) {
    case 'unit':
      return unwrap(
        await api.PATCH('/api/learning-sessions/{sessionId}/units/{unitId}', {
          params: { path: { sessionId, unitId: edit.unitId } },
          body: { excluded: edit.excluded },
        }),
      )
    case 'item':
      return unwrap(
        await api.PATCH('/api/learning-sessions/{sessionId}/items/{itemId}', {
          params: { path: { sessionId, itemId: edit.itemId } },
          body: { excluded: edit.excluded },
        }),
      )
    case 'turn':
      return unwrap(
        await api.PUT('/api/learning-sessions/{sessionId}/turns/{index}', {
          params: { path: { sessionId, index: edit.index } },
          body: edit.content,
        }),
      )
    case 'insert':
      return unwrap(
        await api.POST('/api/learning-sessions/{sessionId}/turns', {
          params: { path: { sessionId } },
          body: { afterIndex: edit.afterIndex, ...edit.content, aiVerdict: null, correction: null, sourceOf: edit.sourceOf },
        }),
      )
    case 'confirm':
      return unwrap(await api.POST('/api/learning-sessions/{sessionId}/confirm', { params: { path: { sessionId } } }))
  }
}

/** 확인 대기 중 세션을 고치거나 확인을 마친다. 응답(재검증한 상세)으로 화면을 바로 바꾼다. */
export function useEditLearningSession(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (edit: Edit) => sendEdit(id, edit),
    onSuccess: (detail) => {
      queryClient.setQueryData(detailKey(id), detail)
      queryClient.invalidateQueries({ queryKey: ['learning-sessions'], exact: true })
    },
  })
}

/** 학습 목표 선택지와 현재 선택(스펙 §7.8). */
export function useLearningGoals(id: number, enabled: boolean) {
  return useQuery({
    enabled,
    queryKey: ['learning-goals', id],
    queryFn: async () =>
      mockEnabled
        ? mockCall(() => mockSessionApi.goals(id))
        : unwrap(await api.GET('/api/sessions/{sessionId}/learning-goals', { params: { path: { sessionId: id } } })),
  })
}

/** 기억 강도 선택지와 예상 하루 부담(스펙 §6.4.7). `current`는 고르기 전이면 null이다. */
export const memoryStrengthQuery = (id: number) => ({
  queryKey: ['memory-strength', id],
  queryFn: async () =>
    mockEnabled
      ? mockCall(() => mockSessionApi.strength(id))
      : unwrap(await api.GET('/api/sessions/{sessionId}/memory-strength', { params: { path: { sessionId: id } } })),
})

export function useMemoryStrength(id: number, enabled: boolean) {
  return useQuery({ ...memoryStrengthQuery(id), enabled })
}

/** 학습 목표와 기억 강도를 함께 저장한다. 저장하면 서버가 첫 학습 문제 생성을 시작한다. */
export function useChooseGoals(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ goals, strength }: { goals: Goal[]; strength: Strength }) => {
      if (mockEnabled) return mockCall(() => mockSessionApi.chooseGoals(id, goals, strength))
      const body: Schemas['GoalsRequest'] = { goals, strength }
      return unwrap(await api.PUT('/api/sessions/{sessionId}/learning-goals', { params: { path: { sessionId: id } }, body }))
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['first-study', id] })
      queryClient.invalidateQueries({ queryKey: ['learning-goals', id] })
    },
  })
}

/** 첫 학습 문제 준비 상태. 생성 중이면 2초마다 다시 읽는다. */
export function useFirstStudy(id: number, enabled: boolean) {
  const queryClient = useQueryClient()
  return useQuery({
    enabled,
    queryKey: ['first-study', id],
    queryFn: async () => {
      const result = mockEnabled
        ? await mockCall(() => mockSessionApi.firstStudy(id))
        : unwrap(await api.GET('/api/sessions/{sessionId}/first-study', { params: { path: { sessionId: id } } }))
      if (result.sessionStatus !== 'CONFIRMED') queryClient.invalidateQueries({ queryKey: ['learning-sessions'] })
      return result
    },
    refetchInterval: (query) => (query.state.data?.generating ? 2000 : false),
  })
}
