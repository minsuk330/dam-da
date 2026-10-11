import { useQuery } from '@tanstack/react-query'
import { Linking } from 'react-native'

import { api, unwrap, type Schemas } from './client'
import { mockAuthToken, mockEnabled, mockResponse } from './mock'

// 웹 소셜 로그인(구글·카카오, 스펙 §7.9). 토스 인앱 빌드(#149)는 이 모듈을 metro-shims/toss/social-login.ts로 바꿔 끼운다
// (서비스 오픈 정책 2-3: 미니앱 안에서는 토스 로그인 외 로그인을 쓸 수 없다. 검수는 번들 안의 코드도 본다).
// 흐름: 로그인 버튼 → 서버 loginUrl → 서버가 {앱}/auth/callback?code=…로 돌려보냄 → exchangeCode로 앱 토큰을 받는다.

export type Provider = 'google' | 'kakao'
export type LoginOptions = Schemas['LoginOptions']

const MOCK_OPTIONS: LoginOptions = {
  providers: [
    { id: 'kakao', name: '카카오', loginUrl: '' },
    { id: 'google', name: '구글', loginUrl: '' },
  ],
}

/** 켜진 로그인 제공자. */
export function useLoginOptions() {
  return useQuery({
    queryKey: ['auth', 'options'],
    queryFn: async () => (mockEnabled ? mockResponse(MOCK_OPTIONS) : unwrap(await api.GET('/api/auth/options'))),
    staleTime: Infinity,
  })
}

/** 서버 로그인 페이지로 떠난다. 웹은 같은 창에서 이동하고, 끝나면 /auth/callback으로 돌아온다. */
export async function openLoginPage(provider: Provider, options?: LoginOptions) {
  const loginUrl = options?.providers.find((p) => p.id === provider)?.loginUrl
  if (!loginUrl) throw new Error('이 로그인 방법은 지금 쓸 수 없어요.')
  if (typeof window !== 'undefined' && window.location) window.location.assign(loginUrl)
  else await Linking.openURL(loginUrl)
}

/** 서버가 돌려보낸 1회용 코드를 앱 토큰으로 바꾼다(/auth/callback). */
export async function exchangeCode(code: string): Promise<Schemas['AuthToken']> {
  if (mockEnabled) return mockResponse(mockAuthToken())
  return unwrap(await api.POST('/api/auth/token', { body: { code } }))
}
