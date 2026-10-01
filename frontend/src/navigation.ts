import { router } from 'expo-router'

import type { Schemas } from '@/api/client'

type Status = Schemas['LearningSessionSummary']['status']

/**
 * 학습 세션 열기. 학습을 시작한 세션은 확인할 것이 없으므로 기억 상태(세션 상세)로, 그 전 세션은 세션 확인으로 간다.
 * 상태를 모르면(아직 목록을 못 읽었으면) 세션 확인으로 간다.
 */
export function openSession(id: number, status: Status | undefined) {
  const params = { id: String(id) }
  if (status === 'IN_PROGRESS') router.push({ pathname: '/sessions/[id]/memory', params })
  else router.push({ pathname: '/sessions/[id]', params })
}

/**
 * 알림 열기 (스펙 §7.7). 학습 내용 도착 알림은 그 세션의 확인 화면으로 간다.
 * 매일 학습 알림은 이미 시작한 풀이가 있으면 이어서 풀고, 없으면 홈의 오늘의 학습으로 간다.
 */
export function openNotification(notification: Schemas['NotificationView']) {
  if (notification.type === 'SESSION_READY') {
    router.push({ pathname: '/sessions/[id]', params: { id: String(notification.targetId) } })
  } else if (notification.type === 'DAILY_LEARNING') {
    const practiceId = dailyPracticeOf(notification)
    if (practiceId === null) router.navigate('/')
    else router.push({ pathname: '/review', params: { practiceId: String(practiceId) } })
  }
}

/**
 * 매일 학습 알림의 시작한 풀이 ID. 시작 전에 보낸 알림은 targetId가 null인데,
 * 계약은 non-null로 생성된다(백엔드 NotificationView.targetId에 @Nullable이 없다). 계약이 고쳐지면 이 보정을 지운다.
 */
export function dailyPracticeOf(notification: Schemas['NotificationView']): number | null {
  return (notification.targetId as number | null) ?? null
}
