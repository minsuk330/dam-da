import { useQuery } from '@tanstack/react-query'
import { api, unwrap } from './client'

export function useConversations() {
  return useQuery({
    queryKey: ['conversations'],
    queryFn: async () => unwrap(await api.GET('/api/conversations')),
  })
}

export function useConversation(id: string) {
  return useQuery({
    queryKey: ['conversations', id],
    queryFn: async () => unwrap(await api.GET('/api/conversations/{id}', { params: { path: { id } } })),
  })
}
