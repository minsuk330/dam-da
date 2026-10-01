import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { api, unwrap, type Schemas } from './client'
import { mockEnabled, mockNotifications, mockResponse } from './mock'

/** 앱 안 알림 목록과 읽지 않은 수. */
export function useNotifications() {
  return useQuery({
    queryKey: ['notifications'],
    queryFn: async () => (mockEnabled ? mockResponse(mockNotifications) : unwrap(await api.GET('/api/notifications'))),
  })
}

/** 알림 1개를 읽음으로 바꾼다. 화면은 응답을 기다리지 않고 바로 읽음으로 보여준다. */
export function useMarkNotificationRead() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (id: number) => {
      if (mockEnabled) {
        const item = mockNotifications.items.find((n) => n.id === id)
        if (item && !item.read) {
          item.read = true
          mockNotifications.unreadCount -= 1
        }
        return
      }
      const result = await api.POST('/api/notifications/{id}/read', { params: { path: { id } } })
      if (!result.response.ok) throw new Error(`API ${result.response.status}`)
    },
    onMutate: (id) => {
      queryClient.setQueryData<Schemas['NotificationsView']>(['notifications'], (old) =>
        old && {
          unreadCount: Math.max(0, old.unreadCount - (old.items.some((n) => n.id === id && !n.read) ? 1 : 0)),
          items: old.items.map((n) => (n.id === id ? { ...n, read: true } : n)),
        },
      )
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['notifications'] }),
  })
}
