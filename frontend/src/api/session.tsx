import { createContext, useContext, useState, type ReactNode } from 'react'

import { mockEnabled, mockResponse } from './mock'

// 앱 로그인 상태 (스펙 §7.9). 구글·카카오 소셜 로그인만 둔다. 소셜 인증은 서버가 하고 앱은 서버가 준 토큰을 쓴다.
// 서버 쪽(#95)이 나오기 전이라 mock에서만 로그인 게이트를 켠다. 실제 API 빌드는 게이트 없이 데모 사용자로 동작한다.

export type Provider = 'google' | 'kakao' | 'demo'

/** 로그인 게이트를 켤지. #95(앱 토큰 발급)가 나오면 실제 API에서도 켠다. */
export const authGateEnabled = mockEnabled

/** "데모 계정으로 시작"을 보일지. 스펙상 개발 도구가 켜진 서버에서만이고, 그 여부는 #95에서 서버가 알려준다. */
export const demoLoginAvailable = mockEnabled

const STORAGE_KEY = 'khack.signedIn'

/** 웹은 새로고침해도 로그인 상태를 유지한다. 저장소를 못 쓰면(사생활 보호 모드 등) 이번 방문 동안만 유지한다. */
function readStored(): boolean {
  try {
    return globalThis.localStorage?.getItem(STORAGE_KEY) === 'true'
  } catch {
    return false
  }
}

function store(signedIn: boolean) {
  try {
    if (signedIn) globalThis.localStorage?.setItem(STORAGE_KEY, 'true')
    else globalThis.localStorage?.removeItem(STORAGE_KEY)
  } catch {
    // 저장하지 못해도 화면 상태는 바뀐다.
  }
}

type Session = {
  signedIn: boolean
  signIn: (provider: Provider) => Promise<void>
  signOut: () => void
}

const SessionContext = createContext<Session | null>(null)

export function SessionProvider({ children }: { children: ReactNode }) {
  const [signedIn, setSignedIn] = useState(() => !authGateEnabled || readStored())

  async function signIn(provider: Provider) {
    if (!mockEnabled) {
      // #95 전에는 서버에 소셜 로그인이 없다. 실제 API 빌드는 게이트가 꺼져 있어 여기 오지 않는다.
      throw new Error(`${provider} 로그인은 아직 서버에서 지원하지 않아요.`)
    }
    await mockResponse(null)
    store(true)
    setSignedIn(true)
  }

  function signOut() {
    store(false)
    setSignedIn(false)
  }

  return <SessionContext.Provider value={{ signedIn, signIn, signOut }}>{children}</SessionContext.Provider>
}

export function useSession(): Session {
  const session = useContext(SessionContext)
  if (!session) throw new Error('SessionProvider 밖에서 useSession을 썼어요.')
  return session
}
