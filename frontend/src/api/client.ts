import createClient from 'openapi-fetch'
import type { components, paths } from './schema'

/** 같은 출처 호출. 로컬은 Vite proxy, 배포는 Vercel rewrites가 Spring으로 넘긴다. */
export const api = createClient<paths>()

export type Schemas = components['schemas']

export class ApiError extends Error {
  readonly status: number

  constructor(status: number) {
    super(`API ${status}`)
    this.status = status
  }
}

/** openapi-fetch 결과에서 data를 꺼내고, 실패 응답은 ApiError로 던진다(TanStack Query가 error로 받는다). */
export function unwrap<T>(result: { data?: T; response: Response }): T {
  if (!result.response.ok || result.data === undefined) {
    throw new ApiError(result.response.status)
  }
  return result.data
}
