import { useQueries, useQuery } from '@tanstack/react-query'

import { DEFAULT_TARGET_RETENTION } from '@/components/gauge'

import { api, unwrap, type Schemas } from './client'
import { memoryStrengthQuery, useLearningSessions, useMemoryStrength } from './learning-sessions'
import { mockEnabled, mockResponse } from './mock'
import { mockSessionApi } from './mock-sessions'

// 기억 게이지(스펙 §6.4.3). R은 서버가 FSRS로 계산하고, 화면은 목표 유지율(§6.4.7)과 비교해 색 단계를 정한다.

export type SessionGauge = Schemas['SessionGauge']
export type UnitView = Schemas['UnitView']
export type Summary = Schemas['Summary']
type Session = Schemas['LearningSessionSummary']

/** mock 함수가 던진 ApiError도 실제 요청처럼 늦게 거절한다. */
async function mockCall<T>(run: () => T): Promise<T> {
  return mockResponse(null).then(() => structuredClone(run()))
}

/** 확인을 마친 세션만 기억 항목이 확정된다. 그 전 세션은 게이지를 부르지 않는다. */
const CONFIRMED_OR_LATER: Session['status'][] = ['CONFIRMED', 'QUESTIONS_READY', 'IN_PROGRESS']

export const hasGauge = (status: Session['status']) => CONFIRMED_OR_LATER.includes(status)

const gaugeQuery = (id: number) => ({
  queryKey: ['memory-gauge', id],
  queryFn: async () =>
    mockEnabled
      ? mockCall(() => mockSessionApi.gauge(id))
      : unwrap(await api.GET('/api/sessions/{sessionId}/memory-gauge', { params: { path: { sessionId: id } } })),
})

/** 세션의 복습 단위·기억 항목별 게이지. */
export function useSessionGauge(id: number, enabled = true) {
  return useQuery({ ...gaugeQuery(id), enabled })
}

/** 첫 학습 요약: 확인한 항목·도움이 필요했던 항목·아직 확인 전, 항목별 다음 복습 시각. */
export function useFirstStudySummary(id: number, enabled = true) {
  return useQuery({
    enabled,
    queryKey: ['first-study-summary', id],
    queryFn: async () =>
      mockEnabled
        ? mockCall(() => mockSessionApi.summary(id))
        : unwrap(await api.GET('/api/sessions/{sessionId}/first-study/summary', { params: { path: { sessionId: id } } })),
  })
}

/** 고른 기억 강도의 목표 유지율. 고르기 전이면 기본값. */
export function targetOf(strength: Schemas['Options'] | undefined): number {
  return strength?.options.find((o) => o.strength === strength.current)?.desiredRetention ?? DEFAULT_TARGET_RETENTION
}

export function useTargetRetention(id: number, enabled = true): number {
  return targetOf(useMemoryStrength(id, enabled).data)
}

export type ItemValue = { memoryItemId: number; content: string; value: number }

/** 확인한 항목들의 R 평균과 가장 약한 항목. 확인한 항목이 없으면 둘 다 null. */
export function gaugeOf(units: UnitView[]): { value: number | null; weakest: ItemValue | null } {
  const items = units.flatMap((u) => u.items)
  const checked = items.flatMap((i) =>
    i.gauge.retrievability === null ? [] : [{ memoryItemId: i.memoryItemId, content: i.content, value: i.gauge.retrievability }],
  )
  if (checked.length === 0) return { value: null, weakest: null }
  const weakest = checked.reduce((low, i) => (i.value < low.value ? i : low))
  return { value: checked.reduce((sum, i) => sum + i.value, 0) / checked.length, weakest }
}

export type SessionMemory = {
  session: Session
  /** 아직 확인 전이면 null. */
  value: number | null
  target: number
  weakest: ItemValue | null
}

/**
 * 기억 탭·홈이 쓰는 세션별 게이지 요약. 확인을 마친 세션마다 게이지와 기억 강도를 불러온다.
 * `average`·`target`은 확인한 항목이 있는 세션들의 평균, `weakest`는 그중 가장 약한 세션이다.
 */
export function useMemoryOverview() {
  const sessions = useLearningSessions()
  const gauged = (sessions.data ?? []).filter((s) => hasGauge(s.status))
  const gauges = useQueries({ queries: gauged.map((s) => gaugeQuery(s.id)) })
  const strengths = useQueries({ queries: gauged.map((s) => memoryStrengthQuery(s.id)) })

  const bySession = new Map<number, SessionMemory>()
  gauged.forEach((session, i) => {
    const gauge = gauges[i].data
    const { value, weakest } = gauge ? gaugeOf(gauge.units) : { value: null, weakest: null }
    bySession.set(session.id, { session, value, target: targetOf(strengths[i].data), weakest })
  })
  const measured = [...bySession.values()].filter((m) => m.value !== null)
  const mean = (values: number[]) => values.reduce((sum, v) => sum + v, 0) / values.length

  return {
    sessions,
    isPending: sessions.isPending || gauges.some((q) => q.isPending),
    isError: sessions.isError || gauges.some((q) => q.isError),
    refetch: () => {
      sessions.refetch()
      gauges.forEach((q) => q.refetch())
    },
    bySession,
    average: measured.length === 0 ? null : mean(measured.map((m) => m.value!)),
    target: measured.length === 0 ? DEFAULT_TARGET_RETENTION : mean(measured.map((m) => m.target)),
    weakest: measured.reduce<SessionMemory | null>((low, m) => (low === null || m.value! < low.value! ? m : low), null),
  }
}
