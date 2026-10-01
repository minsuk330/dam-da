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
