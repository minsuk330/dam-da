import type { Schemas } from './client'
import { ApiError } from './client'
import { mockLearningSessions } from './mock'
import { mockSessionApi } from './mock-sessions'

type FieldOption = Schemas['FieldOption']
type FieldLabel = Schemas['FieldLabel']
type Graph = Schemas['KnowledgeGraph']
type GraphNode = Schemas['Node']

/** 분류표 일부(스펙 §7.10). 실제 분류표는 서버 `field-taxonomy.yml`(대분류 12개, 소분류 78개)이다. */
export const mockFields: FieldOption[] = [
  {
    code: 'cs',
    label: '컴퓨터·IT',
    subfields: [
      { code: 'cs.algo', label: '자료구조·알고리즘', hint: null },
      { code: 'cs.os', label: '운영체제', hint: null },
      { code: 'cs.db', label: '데이터베이스', hint: null },
      { code: 'cs.network', label: '네트워크', hint: null },
      { code: 'cs.frontend', label: '프론트엔드·모바일', hint: null },
      { code: 'cs.etc', label: '기타(컴퓨터·IT)', hint: null },
    ],
  },
  {
    code: 'biz',
    label: '경영·경제',
    subfields: [
      { code: 'biz.accounting', label: '회계', hint: null },
      { code: 'biz.marketing', label: '마케팅', hint: null },
      { code: 'biz.etc', label: '기타(경영·경제)', hint: null },
    ],
  },
  {
    code: 'math',
    label: '수학·통계',
    subfields: [
      { code: 'math.stats', label: '확률·통계', hint: null },
      { code: 'math.etc', label: '기타(수학·통계)', hint: null },
    ],
  },
  {
    code: 'etc',
    label: '기타',
    subfields: [
      { code: 'etc.life', label: '생활·교양', hint: null },
      { code: 'etc.etc', label: '미분류', hint: null },
    ],
  },
]

function labelOf(code: string): FieldLabel {
  const field = mockFields.find((f) => f.subfields.some((s) => s.code === code))
  const subfield = field?.subfields.find((s) => s.code === code)
  if (!field || !subfield) throw new ApiError(400, `분류표에 없는 소분류입니다: ${code}`)
  return { code, label: subfield.label, fieldCode: field.code, fieldLabel: field.label, source: 'USER' }
}

/** 사용자가 분야를 고른다. 목록과 상세의 라벨을 함께 바꾼다. */
export function mockChooseField(sessionId: number, code: string) {
  const label = labelOf(code)
  const summary = mockLearningSessions.find((s) => s.id === sessionId)
  if (summary) summary.field = label
  const detail = mockSessionApi.detail(sessionId)
  detail.field = label
  return detail
}

/** 세션의 복습 단위별 (제목, 확인된 항목 R들, 전체 항목 수). 기억 게이지 mock과 같은 값을 쓴다. */
function unitsOf(sessionId: number): { title: string; checked: number[]; total: number }[] {
  return mockSessionApi.gauge(sessionId).units.map((unit) => ({
    title: unit.title,
    checked: unit.items.flatMap((i) => (i.gauge.retrievability === null ? [] : [i.gauge.retrievability])),
    total: unit.items.length,
  }))
}

const TARGET = 0.9

function mean(values: number[]): number | null {
  return values.length === 0 ? null : values.reduce((sum, v) => sum + v, 0) / values.length
}

/** 서버 KnowledgeGraphService와 같은 모양: 대분류 → 소분류 → 세션 → 복습 단위, R은 확인된 항목 평균. */
export function mockGraph(): Graph {
  const nodes: GraphNode[] = []
  const unclassified = labelOf('etc.etc')
  const sessions = mockLearningSessions.map((s) => ({ session: s, label: s.field ?? unclassified, units: unitsOf(s.id) }))
  const node = (id: string, kind: GraphNode['kind'], label: string, parentId: string | null, sessionId: number | null,
    units: { checked: number[]; total: number }[]): GraphNode => {
    const checked = units.flatMap((u) => u.checked)
    return {
      id, kind, label, parentId, sessionId,
      retrievability: mean(checked),
      targetRetention: TARGET,
      checkedItems: checked.length,
      totalItems: units.reduce((sum, u) => sum + u.total, 0),
    }
  }
  for (const field of mockFields) {
    const inField = sessions.filter((s) => s.label.fieldCode === field.code)
    if (inField.length === 0) continue
    nodes.push(node(`field:${field.code}`, 'FIELD', field.label, null, null, inField.flatMap((s) => s.units)))
    for (const subfield of field.subfields) {
      const inSub = inField.filter((s) => s.label.code === subfield.code)
      if (inSub.length === 0) continue
      nodes.push(node(`subfield:${subfield.code}`, 'SUBFIELD', subfield.label, `field:${field.code}`, null, inSub.flatMap((s) => s.units)))
      for (const { session, units } of inSub) {
        nodes.push(node(`session:${session.id}`, 'SESSION', session.topicHint ?? `학습 세션 ${session.id}`,
          `subfield:${subfield.code}`, session.id, units))
        units.forEach((unit, i) =>
          nodes.push(node(`unit:${session.id}-${i}`, 'UNIT', unit.title, `session:${session.id}`, session.id, [unit])))
      }
    }
  }
  const edges = nodes.flatMap((n) => (n.parentId ? [{ source: n.parentId, target: n.id }] : []))
  return { nodes, edges }
}
