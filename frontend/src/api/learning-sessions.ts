import { useQuery } from '@tanstack/react-query'

import { api, unwrap } from './client'
import { mockEnabled, mockLearningSessions, mockResponse } from './mock'

/** 학습 세션 목록. 최근 것부터. */
export function useLearningSessions() {
  return useQuery({
    queryKey: ['learning-sessions'],
    queryFn: async () =>
      mockEnabled ? mockResponse(mockLearningSessions) : unwrap(await api.GET('/api/learning-sessions')),
  })
}
