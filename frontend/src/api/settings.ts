import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { ApiError, api, unwrap, type Schemas } from './client'
import { mockEnabled, mockResponse } from './mock'

// 매일 학습 설정(스펙 §7.7): 하루 학습 시간(1~60분)과 알림 시각. 범위 검사는 서버가 하고 400이면 message를 준다.

export type DailySettings = Schemas['Settings']

/** mock 설정. 저장한 값을 mock 안에서 유지하도록 변경 가능한 객체로 둔다. */
const mockSettings: DailySettings = { budgetMinutes: 10, notifyAt: '08:00:00' }

/** 하루 학습 시간과 알림 시각. `notifyAt`이 null이면 알림을 끈 상태다. */
export function useDailySettings() {
  return useQuery({
    queryKey: ['daily-settings'],
    queryFn: async () =>
      mockEnabled ? mockResponse({ ...mockSettings }) : unwrap(await api.GET('/api/settings/daily')),
  })
}

/** 설정을 바꾼다. 하루 학습 시간이 바뀌면 오늘의 학습 편성도 달라지므로 다시 불러온다. */
export function useChangeDailySettings() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (next: DailySettings) => {
      if (!mockEnabled) return unwrap(await api.PUT('/api/settings/daily', { body: next }))
      await mockResponse(null)
      if (next.budgetMinutes < 1 || next.budgetMinutes > 60) {
        throw new ApiError(400, '하루 학습 시간은 1~60분 사이로 정하세요.')
      }
      Object.assign(mockSettings, next)
      return { ...mockSettings }
    },
    onSuccess: (saved) => {
      queryClient.setQueryData(['daily-settings'], saved)
      queryClient.invalidateQueries({ queryKey: ['daily'] })
    },
  })
}

/** 서버는 "07:30" 또는 "07:30:00"으로 준다. 화면 비교는 "HH:mm"으로 한다. */
export function hourMinute(time: string): string {
  return time.slice(0, 5)
}
