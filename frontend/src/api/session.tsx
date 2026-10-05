import { useQuery, useQueryClient } from '@tanstack/react-query'
import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react'
import { Linking } from 'react-native'

import { api, ApiError, unwrap, type Schemas } from './client'
import { mockEnabled, mockResponse } from './mock'

// 앱 로그인 상태 (스펙 §7.9). 구글·카카오 소셜 로그인만 둔다. 토스 인앱(#149)에서는 토스 로그인만 쓴다. 소셜 인증은 서버가 하고 앱은 서버가 준 토큰을 쓴다.
// 흐름: 로그인 버튼 → 서버 loginUrl(구글·카카오) → 서버가 {앱}/auth/callback?code=…로 돌려보냄 → POST /api/auth/token으로
// 앱 토큰을 받아 저장 → 모든 /api 요청에 Bearer로 붙인다. 401이 오면 로그아웃해 메인 화면으로 돌아간다.

export type Provider = 'google' | 'kakao'
export type AuthToken = Schemas['AuthToken']
export type LoginOptions = Schemas['LoginOptions']

const STORAGE_KEY = 'khack.auth'

/** 웹은 새로고침해도 로그인 상태를 유지한다. 저장소를 못 쓰면(사생활 보호 모드 등) 이번 방문 동안만 유지한다. */
function readStored(): AuthToken | null {
  try {
    const raw = globalThis.localStorage?.getItem(STORAGE_KEY)
    if (!raw) return null
    const token = JSON.parse(raw) as AuthToken
    return Date.parse(token.expiresAt) > Date.now() ? token : null
  } catch {
    return null
  }
}

function store(token: AuthToken | null) {
  try {
    if (token) globalThis.localStorage?.setItem(STORAGE_KEY, JSON.stringify(token))
    else globalThis.localStorage?.removeItem(STORAGE_KEY)
  } catch {
    // 저장하지 못해도 화면 상태는 바뀐다.
  }
}

// 요청마다 토큰을 붙이는 미들웨어는 React 밖에 있으므로 현재 토큰과 401 처리기를 모듈에 둔다.
let current: AuthToken | null = readStored()
let onUnauthorized: () => void = () => {}

api.use({
  onRequest({ request }) {
    if (current) request.headers.set('Authorization', `Bearer ${current.accessToken}`)
    return request
  },
  onResponse({ request, response }) {
    // 로그인 API 자체의 401(코드 만료 등)은 화면이 오류로 보여준다.
    if (response.status === 401 && current && !new URL(request.url, 'http://local').pathname.startsWith('/api/auth/')) {
      onUnauthorized()
    }
    return response
  },
})

const MOCK_OPTIONS: LoginOptions = {
  providers: [
    { id: 'kakao', name: '카카오', loginUrl: '' },
    { id: 'google', name: '구글', loginUrl: '' },
  ],
}

function mockToken(): AuthToken {
  return { accessToken: 'mock', expiresAt: new Date(Date.now() + 30 * 86_400_000).toISOString(), user: { id: 1, name: '지원' } }
}

/** 켜진 로그인 제공자. */
export function useLoginOptions() {
  return useQuery({
    queryKey: ['auth', 'options'],
    queryFn: async () => (mockEnabled ? mockResponse(MOCK_OPTIONS) : unwrap(await api.GET('/api/auth/options'))),
    staleTime: Infinity,
  })
}

/** 서버가 돌려보낸 1회용 코드를 앱 토큰으로 바꾼다(/auth/callback). */
export async function exchangeCode(code: string): Promise<AuthToken> {
  if (mockEnabled) return mockResponse(mockToken())
  return unwrap(await api.POST('/api/auth/token', { body: { code } }))
}

type Session = {
  signedIn: boolean
  user: AuthToken['user'] | null
  /** 구글·카카오 서버 로그인 페이지로 떠난다(돌아오면 /auth/callback). */
  signIn: (provider: Provider, options?: LoginOptions) => Promise<void>
  /** 토스 인앱(#149). 토스 로그인 창에서 받은 인가 코드를 서버가 앱 토큰으로 바꾼다. 사용자가 창을 닫으면 오류를 던진다. */
  signInWithToss: () => Promise<void>
  /** /auth/callback에서 받은 토큰으로 로그인 상태를 만든다. */
  complete: (token: AuthToken) => void
  signOut: () => void
  /** 회원 탈퇴(#148). 서버가 이 사용자의 데이터를 모두 지운 뒤 로그아웃한다. 실패하면 로그인 상태를 그대로 두고 오류를 던진다. */
  deleteAccount: () => Promise<void>
}

const SessionContext = createContext<Session | null>(null)

export function SessionProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [token, setToken] = useState<AuthToken | null>(current)

  const apply = useCallback(
    (next: AuthToken | null) => {
      // 다른 계정의 데이터가 남지 않게 로그인·로그아웃 때 불러온 데이터를 비운다.
      if (next?.accessToken !== current?.accessToken) queryClient.clear()
      current = next
      store(next)
      setToken(next)
    },
    [queryClient],
  )

  useEffect(() => {
    onUnauthorized = () => apply(null)
    return () => {
      onUnauthorized = () => {}
    }
  }, [apply])

  async function signIn(provider: Provider, options?: LoginOptions) {
    if (mockEnabled) {
      apply(await mockResponse(mockToken()))
      return
    }
    const loginUrl = options?.providers.find((p) => p.id === provider)?.loginUrl
    if (!loginUrl) throw new Error('이 로그인 방법은 지금 쓸 수 없어요.')
    // 웹은 같은 창에서 서버 로그인으로 이동한다. 끝나면 /auth/callback으로 돌아온다.
    if (typeof window !== 'undefined' && window.location) window.location.assign(loginUrl)
    else await Linking.openURL(loginUrl)
  }

  async function signInWithToss() {
    if (mockEnabled) {
      apply(await mockResponse(mockToken()))
      return
    }
    // 토스 SDK는 토스 빌드에서만 쓰므로 일반 웹 번들에 섞이지 않게 필요할 때 불러온다.
    const { appLogin } = await import('@apps-in-toss/web-framework')
    const { authorizationCode, referrer } = await appLogin()
    apply(unwrap(await api.POST('/api/auth/toss', { body: { authorizationCode, referrer } })))
  }

  async function deleteAccount() {
    if (mockEnabled) {
      await mockResponse(null)
    } else {
      const { response } = await api.DELETE('/api/me')
      if (!response.ok) throw new ApiError(response.status)
    }
    apply(null)
  }

  return (
    <SessionContext.Provider
      value={{
        signedIn: token !== null,
        user: token?.user ?? null,
        signIn,
        signInWithToss,
        complete: apply,
        signOut: () => apply(null),
        deleteAccount,
      }}>
      {children}
    </SessionContext.Provider>
  )
}

export function useSession(): Session {
  const session = useContext(SessionContext)
  if (!session) throw new Error('SessionProvider 밖에서 useSession을 썼어요.')
  return session
}
