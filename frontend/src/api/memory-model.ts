import { useQuery } from '@tanstack/react-query'

import { api, unwrap, type Schemas } from './client'
import { mockEnabled, mockResponse } from './mock'

// 내 기억 패턴(스펙 §6.4.9). 곡선·유지 기간·개선률은 모두 서버가 FSRS로 계산한다.

export type MemoryModel = Schemas['MemoryModel']
export type CurvePoint = Schemas['CurvePoint']

/** mock 곡선: FSRS-6 망각 곡선 R(t) = (1 + factor·t/S)^(-decay). S일 뒤 R이 0.9가 된다. */
function mockCurve(stability: number, decay = 0.2): CurvePoint[] {
  const factor = 0.9 ** (-1 / decay) - 1
  return Array.from({ length: 31 }, (_, day) => ({ day, retrievability: (1 + (factor * day) / stability) ** -decay }))
}

/** 기록이 쌓이는 중인 사용자(메인 데모 사용자). */
const mockDefault: MemoryModel = {
  status: 'DEFAULT',
  parametersVersion: 1,
  progress: { gradedReviews: 32, requiredReviews: 1000 },
  curve: mockCurve(3.2602),
  defaultCurve: null,
  firstRecallDays: 3.2602,
  typicalStabilityDays: 4.1,
  reviewedItems: 9,
  predictionImprovement: null,
}

/** 합성 기록으로 개인화한 사용자(#71). 기본값보다 빨리 잊는다. */
const mockPersonalized: MemoryModel = {
  status: 'PERSONALIZED',
  parametersVersion: 4,
  progress: { gradedReviews: 2009, requiredReviews: 1000 },
  curve: mockCurve(2.28, 0.28),
  defaultCurve: mockCurve(3.2602),
  firstRecallDays: 2.28,
  typicalStabilityDays: 16.8,
  reviewedItems: 200,
  predictionImprovement: 0.067,
}

/** mock에서 개인화 화면을 보려면 EXPO_PUBLIC_MOCK_MEMORY_MODEL=personalized 로 실행한다. */
const mockModel = process.env.EXPO_PUBLIC_MOCK_MEMORY_MODEL === 'personalized' ? mockPersonalized : mockDefault

export function useMemoryModel() {
  return useQuery({
    queryKey: ['memory-model'],
    queryFn: async () => (mockEnabled ? mockResponse(mockModel) : unwrap(await api.GET('/api/memory/model'))),
  })
}
