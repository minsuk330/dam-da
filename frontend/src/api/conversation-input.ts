import { useMutation, useQueryClient } from '@tanstack/react-query'

import { api, type Schemas } from './client'
import { mockEnabled, mockIntake, mockResponse } from './mock'

/** 입력 API가 거부한 이유. `fallback`이 'paste'면 공유 링크 대신 붙여넣기를 권한다. */
export class IntakeError extends Error {
  readonly status: number
  readonly fallback: string | null

  constructor(status: number, message: string, fallback: string | null) {
    super(message)
    this.status = status
    this.fallback = fallback
  }
}

export type ConversationInput = { kind: 'share_link'; url: string } | { kind: 'paste'; text: string }

function errorBody(error: unknown): { message?: unknown; fallback?: unknown } {
  return typeof error === 'object' && error !== null ? error : {}
}

/** 공유 링크나 붙여넣은 대화를 보내 학습 대화로 저장한다. 성공하면 대화·세션 목록을 다시 불러온다. */
export function useSubmitConversation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (input: ConversationInput): Promise<Schemas['IntakeResponse']> => {
      if (mockEnabled) return mockResponse({ ...mockIntake, inputPath: input.kind }, 1500)
      const result =
        input.kind === 'share_link'
          ? await api.POST('/api/conversations/share-link', { body: { url: input.url } })
          : await api.POST('/api/conversations/paste', { body: { text: input.text } })
      if (result.response.ok && result.data) return result.data
      // 오류 응답은 @ExceptionHandler가 만들어 계약에 타입이 없다. message·fallback만 꺼낸다.
      const body = errorBody(result.error)
      throw new IntakeError(
        result.response.status,
        typeof body.message === 'string' ? body.message : '대화를 저장하지 못했어요. 잠시 후 다시 시도해 주세요.',
        typeof body.fallback === 'string' ? body.fallback : null,
      )
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['conversations'] })
      queryClient.invalidateQueries({ queryKey: ['learning-sessions'] })
    },
  })
}
