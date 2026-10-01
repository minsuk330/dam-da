import { useQuery } from '@tanstack/react-query'

import { api, unwrap, type Schemas } from './client'
import { mockEnabled, mockResponse } from './mock'
import { mockGraph } from './mock-graph'

// 지식 그래프 (스펙 §7.10). 노드의 R은 서버가 FSRS로 계산하고, 화면은 목표 유지율과 비교해 기억 게이지 색을 정한다.

export type GraphNode = Schemas['Node']

export function useKnowledgeGraph(enabled = true) {
  return useQuery({
    enabled,
    queryKey: ['knowledge-graph'],
    queryFn: async () => (mockEnabled ? mockResponse(mockGraph()) : unwrap(await api.GET('/api/knowledge-graph'))),
  })
}
