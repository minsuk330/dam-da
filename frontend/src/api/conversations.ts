import { useQuery } from '@tanstack/react-query'

import { api, ApiError, unwrap } from './client'
import { mockConversation, mockConversations, mockEnabled, mockResponse } from './mock'

export function useConversations() {
  return useQuery({
    queryKey: ['conversations'],
    queryFn: async () =>
      mockEnabled ? mockResponse(mockConversations) : unwrap(await api.GET('/api/conversations')),
  })
}

export function useConversation(id: string) {
  return useQuery({
    queryKey: ['conversations', id],
    queryFn: async () => {
      if (!mockEnabled) return unwrap(await api.GET('/api/conversations/{id}', { params: { path: { id } } }))
      const detail = mockConversation(id)
      if (!detail) throw new ApiError(404)
      return mockResponse(detail)
    },
  })
}
