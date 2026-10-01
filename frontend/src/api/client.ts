import createClient from 'openapi-fetch'
import type { components, paths } from './schema'

/**
 * API 주소. 배포 웹은 비워 두고 같은 출처로 호출한다(Vercel rewrites가 /api를 Spring으로 넘긴다).
 * 로컬 개발(Expo 개발 서버는 Spring과 포트가 다르다)과 네이티브 앱은 EXPO_PUBLIC_API_URL로 Spring 주소를 준다.
 */
const baseUrl = process.env.EXPO_PUBLIC_API_URL ?? (__DEV__ ? 'http://localhost:8080' : '')

export const api = createClient<paths>({ baseUrl })

export type Schemas = components['schemas']

/** `serverMessage`는 서버 오류 응답의 message(사용자에게 보여줄 수 있는 문장)다. 없으면 null. */
export class ApiError extends Error {
  readonly status: number
  readonly serverMessage: string | null

  constructor(status: number, serverMessage: string | null = null) {
    super(serverMessage ?? `API ${status}`)
    this.status = status
    this.serverMessage = serverMessage
  }
}

/** 오류 응답은 @ExceptionHandler가 만들어 계약에 타입이 없다. message만 꺼낸다. */
function messageOf(error: unknown): string | null {
  if (typeof error !== 'object' || error === null || !('message' in error)) return null
  return typeof error.message === 'string' ? error.message : null
}

/** openapi-fetch 결과에서 data를 꺼내고, 실패 응답은 ApiError로 던진다(TanStack Query가 error로 받는다). */
export function unwrap<T>(result: { data?: T; error?: unknown; response: Response }): T {
  if (!result.response.ok || result.data === undefined) {
    throw new ApiError(result.response.status, messageOf(result.error))
  }
  return result.data
}
