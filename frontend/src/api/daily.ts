import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { api, unwrap, type Schemas } from './client'
import { mockEnabled, mockResponse } from './mock'
import { mockPracticeApi } from './mock-practice'

// 매일 학습(스펙 §6.4.4)과 연속 학습 일수(§7.7). 큐 편성·시간 예산·연속 일수는 서버가 계산한다.

export type Daily = Schemas['DailyView']
export type Streak = Schemas['Streak']

/** mock 함수가 던진 ApiError도 실제 요청처럼 늦게 거절한다. */
async function mockCall<T>(run: () => T): Promise<T> {
  return mockResponse(null).then(() => structuredClone(run()))
}

/** mock 오늘의 학습: 복습 2 + 새 항목 1. 시작하면 풀이 ID가 생기고, 다 풀면 완료다. */
function mockDaily(): Daily {
  const practice = mockPracticeApi.daily()
  return {
    date: new Date().toISOString().slice(0, 10),
    practiceId: practice?.practiceId ?? null,
    started: practice !== null,
    completed: practice?.completed ?? false,
    total: practice?.total ?? 3,
    reviewCount: 2,
    newCount: 1,
    estimatedSeconds: 270,
    carriedOver: 0,
    items: [],
    unavailable: [],
  }
}

/** 오늘의 학습. 시작 전이면 `practiceId`가 null이고 큐 미리보기의 수와 예상 시간을 준다. */
export function useDaily() {
  return useQuery({
    queryKey: ['daily'],
    queryFn: async () => (mockEnabled ? mockCall(mockDaily) : unwrap(await api.GET('/api/daily'))),
  })
}

/**
 * 오늘의 학습을 시작한다(이미 시작했으면 그 풀이). 문제를 고르거나 변형을 만들기 때문에 오래 걸릴 수 있다.
 * 낼 문제가 없으면 `practiceId`가 null이다.
 */
export function useStartDaily() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async () =>
      mockEnabled
        ? mockCall(() => {
            mockPracticeApi.startDaily()
            return mockDaily()
          })
        : unwrap(await api.POST('/api/daily/start')),
    onSuccess: (daily) => {
      queryClient.setQueryData(['daily'], daily)
      queryClient.invalidateQueries({ queryKey: ['streak'] })
    },
  })
}

/** 연속 학습 일수. `today`가 COMPLETED면 오늘 학습을 끝냈고, EMPTY면 오늘은 복습할 것이 없는 날이다. */
export function useStreak() {
  return useQuery({
    queryKey: ['streak'],
    queryFn: async (): Promise<Streak> =>
      mockEnabled
        ? mockCall(() => {
            const done = mockPracticeApi.daily()?.completed ?? false
            return { current: done ? 5 : 4, best: 9, today: done ? 'COMPLETED' : null }
          })
        : unwrap(await api.GET('/api/streak')),
  })
}

/** 예상 시간을 분으로. 문제가 있으면 최소 1분. */
export function minutesOf(daily: Daily): number {
  return daily.total === 0 ? 0 : Math.max(1, Math.round(daily.estimatedSeconds / 60))
}

/** 연속 학습 카드의 설명. */
export function streakLabel(streak: Streak): string {
  if (streak.today === 'COMPLETED') return '연속 학습 중'
  if (streak.today === 'EMPTY') return '오늘은 쉬는 날'
  return `오늘 하면 ${streak.current + 1}일째`
}
