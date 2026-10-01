import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { api, unwrap } from './client'
import { mockResponse, mockEnabled } from './mock'
import { mockChooseField, mockFields } from './mock-graph'

// 학습 분야 라벨 (스펙 §7.10). 서버가 저장 직후 자동으로 정하고, 사용자는 분류표에서 바꿀 수 있다.

/** 분류표: 대분류와 그 소분류. 바뀌지 않으므로 한 번만 읽는다. */
export function useFields(enabled = true) {
  return useQuery({
    enabled,
    queryKey: ['fields'],
    staleTime: Infinity,
    queryFn: async () => (mockEnabled ? mockResponse(mockFields) : unwrap(await api.GET('/api/fields'))),
  })
}

/** 세션의 분야를 고른다. 응답(세션 상세)으로 화면을 바꾸고 목록·그래프를 다시 읽는다. */
export function useChooseField(sessionId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (code: string) =>
      mockEnabled
        ? mockResponse(null).then(() => structuredClone(mockChooseField(sessionId, code)))
        : unwrap(
            await api.PUT('/api/learning-sessions/{sessionId}/field', {
              params: { path: { sessionId } },
              body: { code },
            }),
          ),
    onSuccess: (detail) => {
      queryClient.setQueryData(['learning-sessions', sessionId], detail)
      queryClient.invalidateQueries({ queryKey: ['learning-sessions'], exact: true })
      queryClient.invalidateQueries({ queryKey: ['knowledge-graph'] })
    },
  })
}
