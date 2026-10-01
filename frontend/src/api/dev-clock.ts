import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { ApiError, baseUrl } from './client'
import { mockEnabled, mockResponse } from './mock'

// 시연 도구: 서버 시계 이동(스펙 §11.3 시간 이동 데모). 서버의 `/dev/clock`을 부른다.
// `/dev/**`는 DEV_TOOLS_ENABLED 서버에서만 열리고, 원격 요청은 `X-Dev-Token`이 맞아야 한다(AGENTS.md).
// 토큰은 코드·번들에 넣지 않는다. 시연자가 화면에서 직접 입력하고, 그 브라우저에만 저장한다.
// 시계는 사용자별이 아니라 서버 전체가 함께 움직인다.

/** 서버 시계. `offset`은 ISO-8601 기간(예: "PT216H"). */
export type DevClock = { now: string; offset: string }

const TOKEN_KEY = 'khack.devToolsToken'

function readToken(): string | null {
  try {
    return globalThis.localStorage?.getItem(TOKEN_KEY) ?? null
  } catch {
    return null
  }
}

function writeToken(token: string | null) {
  try {
    if (token) globalThis.localStorage?.setItem(TOKEN_KEY, token)
    else globalThis.localStorage?.removeItem(TOKEN_KEY)
  } catch {
    // 저장하지 못하면 이번 화면에서만 쓴다.
  }
}

/** 입력한 토큰. 웹은 새로고침해도 남고, 저장소를 못 쓰면 이번 방문 동안만 유지한다. */
export function useDevToken() {
  const [token, setToken] = useState(readToken)
  return {
    token,
    save: (value: string) => {
      writeToken(value)
      setToken(value)
    },
    clear: () => {
      writeToken(null)
      setToken(null)
    },
  }
}

/** mock 시계: 기기 시각에 이동한 시간만 더한다. */
let mockHours = 0
function mockClock(): DevClock {
  return { now: new Date(Date.now() + mockHours * 3_600_000).toISOString(), offset: `PT${mockHours}H` }
}

async function devFetch(path: string, token: string | null, method: 'GET' | 'POST' = 'GET'): Promise<DevClock> {
  const response = await fetch(`${baseUrl}${path}`, { method, headers: token ? { 'X-Dev-Token': token } : {} })
  if (!response.ok) {
    // 토큰이 틀리거나 개발 도구가 꺼진 서버는 404를 준다(경로가 있는지도 숨긴다).
    const body = await response.json().catch(() => null)
    throw new ApiError(response.status, typeof body?.message === 'string' ? body.message : null)
  }
  return response.json()
}

/** 지금 서버 시계. 토큰이 없으면 부르지 않는다(로컬 개발 서버는 토큰 없이도 열린다). */
export function useDevClock(token: string | null) {
  const needsToken = !mockEnabled && !__DEV__
  return useQuery({
    queryKey: ['dev-clock', token],
    enabled: !needsToken || token !== null,
    retry: false,
    queryFn: () => (mockEnabled ? mockResponse(mockClock()) : devFetch('/dev/clock', token)),
  })
}

/** 서버 시계를 앞으로 옮기거나(`days`) 처음으로 되돌린다(`null`). 옮긴 뒤 모든 화면 데이터를 다시 불러온다. */
export function useMoveDevClock(token: string | null) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (days: number | null) => {
      if (mockEnabled) {
        mockHours = days === null ? 0 : mockHours + days * 24
        return mockResponse(mockClock())
      }
      return devFetch(days === null ? '/dev/clock/reset' : `/dev/clock/travel?days=${days}`, token, 'POST')
    },
    onSuccess: (clock) => {
      queryClient.setQueryData(['dev-clock', token], clock)
      // 날짜가 바뀌면 오늘의 학습·게이지·연속 학습이 모두 달라진다.
      queryClient.invalidateQueries({ predicate: (query) => query.queryKey[0] !== 'dev-clock' })
    },
  })
}

/** "PT216H" → 9(일). 시간 단위만 오므로 시간을 24로 나눈다. */
export function offsetDays(offset: string): number {
  const hours = Number(/PT(\d+)H/.exec(offset)?.[1] ?? 0)
  return Math.floor(hours / 24)
}
