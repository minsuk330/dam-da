import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { api, unwrap, type Schemas } from './client'
import { mockEnabled, mockResponse } from './mock'

// 시연 도구: 서버 시계 이동(스펙 §11.3 시간 이동 데모). 서버의 `/api/demo/clock`을 부른다.
// 로그인한 사용자면 개발 도구 토큰 없이 쓴다. 서버가 DEMO_CLOCK_ENABLED로 켰을 때만 열리고, 꺼져 있으면 404다.
// 시계는 사용자별이 아니라 서버 전체가 함께 움직인다.

/** 서버 시계. `offset`은 ISO-8601 기간(예: "PT216H"). */
export type DevClock = Schemas['DemoClock']

/** mock 시계: 기기 시각에 이동한 시간만 더한다. */
let mockHours = 0
function mockClock(): DevClock {
  return { now: new Date(Date.now() + mockHours * 3_600_000).toISOString(), offset: `PT${mockHours}H` }
}

const clockKey = ['dev-clock'] as const

/** 지금 서버 시계. */
export function useDevClock() {
  return useQuery({
    queryKey: clockKey,
    retry: false,
    queryFn: async () => (mockEnabled ? mockResponse(mockClock()) : unwrap(await api.GET('/api/demo/clock'))),
  })
}

/** 서버 시계를 앞으로 옮기거나(`days`) 처음으로 되돌린다(`null`). 옮긴 뒤 모든 화면 데이터를 다시 불러온다. */
export function useMoveDevClock() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (days: number | null) => {
      if (mockEnabled) {
        mockHours = days === null ? 0 : mockHours + days * 24
        return mockResponse(mockClock())
      }
      return days === null
        ? unwrap(await api.POST('/api/demo/clock/reset'))
        : unwrap(await api.POST('/api/demo/clock/travel', { params: { query: { days } } }))
    },
    onSuccess: (clock) => {
      queryClient.setQueryData(clockKey, clock)
      // 날짜가 바뀌면 오늘의 학습·게이지·연속 학습이 모두 달라진다.
      queryClient.invalidateQueries({ predicate: (query) => query.queryKey[0] !== 'dev-clock' })
    },
  })
}

/** "PT216H" → 9(일). 시간 단위만 오므로 시간을 24로 나눈다. */
export function offsetDays(offset: string): number {
  const hours = Number(/PT(\d+)H/.exec(offset)?.[1] ?? 0)
  return Math.floor(hours / 24)
}
