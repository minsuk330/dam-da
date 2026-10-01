import { ApiError, type Schemas } from './client'
import { mockLearningSessions } from './mock'
import { mockFirstStudyQuestions } from './mock-practice'

// 세션 확인 흐름(발화 확인 → 복습 단위 확인 → 학습 목표·기억 강도 → 문제 준비)의 mock.
// 서버 규칙(analysis/practice 컨텍스트)을 단순하게 흉내 내어, mock에서도 고치고 확인한 결과가 화면에 남는다.

type Detail = Schemas['LearningSessionDetail']
type Turn = Schemas['Turn']
type Goal = Schemas['GoalView']['goal']
type Strength = Schemas['Option']['strength']

const hoursAgo = (hours: number) => new Date(Date.now() - hours * 60 * 60 * 1000).toISOString()

function turn(index: number, text: string, intent: Turn['intent'], aiVerdict: Turn['aiVerdict'] = null, correction: string | null = null): Turn {
  return { index, text, quotedText: null, intent, aiVerdict, correction }
}

const details: Record<number, Detail> = {
  3: {
    id: 3,
    conversationId: 'mock-innodb',
    status: 'AWAITING_CONFIRMATION',
    topicHint: 'InnoDB 잠금과 MVCC',
    inputPath: 'connector',
    fidelity: 'model_transcribed',
    createdAt: hoursAgo(1),
    confirmedAt: null,
    turns: [
      turn(1, 'MVCC가 뭐야?', 'info_request'),
      turn(2, 'SELECT도 S 락 거는 거 맞지?', 'understanding_check', 'corrected', '일반 SELECT는 잠금 없이 스냅샷을 읽는다'),
      turn(3, '그러니까 스냅샷을 읽는 거지?', 'understanding_check', 'confirmed'),
      turn(4, 'FOR UPDATE는 뭐가 달라?', 'info_request'),
      turn(5, '이거 복습에 넣어줘', 'meta'),
    ],
    units: [
      {
        id: 31,
        title: 'MVCC 읽기',
        excluded: false,
        verdict: 'APPROVED',
        verdictReason: null,
        evidenceTurns: [1, 2, 3],
        items: [
          { id: 311, kind: 'FACT', content: 'MVCC는 행의 여러 버전을 두고 트랜잭션마다 맞는 버전을 읽는다', sourceTurns: [1], status: 'NEW' },
          { id: 312, kind: 'FACT', content: '일반 SELECT는 잠금 없이 스냅샷을 읽는다', sourceTurns: [1, 3], status: 'NEW' },
          { id: 313, kind: 'CONFUSION', content: '일반 SELECT도 S 락을 건다', sourceTurns: [2], status: 'NEW' },
        ],
      },
      {
        id: 32,
        title: '잠금 읽기',
        excluded: false,
        verdict: 'HELD',
        verdictReason: '근거 발화에 AI 설명이 짧아 핵심 내용이 맞는지 확인이 필요해요.',
        evidenceTurns: [4],
        items: [{ id: 321, kind: 'WARNING', content: 'SELECT ... FOR UPDATE는 읽은 행에 배타 락을 건다', sourceTurns: [4], status: 'NEW' }],
      },
    ],
    warnings: ['모델이 옮겨 적은 대화예요. 원문과 다른 발화가 있으면 고쳐 주세요.'],
  },
  2: {
    id: 2,
    conversationId: 'mock-stats',
    status: 'QUESTIONS_READY',
    topicHint: '경영통계: 표준오차',
    inputPath: 'share_link',
    fidelity: 'verbatim',
    createdAt: hoursAgo(48),
    confirmedAt: hoursAgo(47),
    turns: [
      turn(1, '표준오차가 뭐야?', 'info_request'),
      turn(2, '표준편차랑 같은 거 아니야?', 'understanding_check', 'corrected', '표준오차는 표본평균의 퍼짐이다'),
      turn(3, '그럼 표본이 커지면 표준오차는 줄어드는 거네?', 'restatement', 'confirmed'),
    ],
    units: [
      {
        id: 21,
        title: '표준오차',
        excluded: false,
        verdict: 'APPROVED',
        verdictReason: null,
        evidenceTurns: [1, 2, 3],
        items: [
          { id: 211, kind: 'FACT', content: '표준오차는 표본평균이 얼마나 퍼지는지 나타낸다', sourceTurns: [1], status: 'NEW' },
          { id: 212, kind: 'FACT', content: '표본 크기가 커지면 표준오차는 √n에 반비례해 줄어든다', sourceTurns: [3], status: 'NEW' },
          { id: 213, kind: 'CONFUSION', content: '표준오차와 표준편차는 같다', sourceTurns: [2], status: 'NEW' },
        ],
      },
    ],
    warnings: [],
  },
  1: {
    id: 1,
    conversationId: 'mock-react',
    status: 'IN_PROGRESS',
    topicHint: 'React 재렌더링',
    inputPath: 'paste',
    fidelity: 'model_transcribed',
    createdAt: hoursAgo(120),
    confirmedAt: hoursAgo(119),
    turns: [
      turn(1, '부모가 렌더링되면 자식도 항상 다시 그려져?', 'info_request'),
      turn(2, 'memo로 감싸면 props가 같을 때 건너뛰는 거지?', 'understanding_check', 'partial', '얕은 비교라서 인라인 객체는 매번 새 값이다'),
    ],
    units: [
      {
        id: 11,
        title: 'memo와 얕은 비교',
        excluded: false,
        verdict: 'APPROVED',
        verdictReason: null,
        evidenceTurns: [1, 2],
        items: [
          { id: 111, kind: 'FACT', content: '부모가 렌더링되면 자식도 기본으로 다시 렌더링된다', sourceTurns: [1], status: 'ACTIVE' },
          { id: 112, kind: 'PRACTICE', content: 'memo는 props를 얕게 비교하므로 인라인 객체는 매번 새 값이다', sourceTurns: [2], status: 'ACTIVE' },
        ],
      },
    ],
    warnings: [],
  },
}

function detail(id: number): Detail {
  const found = details[id]
  if (!found) throw new ApiError(404, '학습 세션을 찾을 수 없어요.')
  return found
}

function editable(id: number): Detail {
  const session = detail(id)
  if (session.status !== 'AWAITING_CONFIRMATION') throw new ApiError(409, '확인 대기 중에만 고칠 수 있어요.')
  return session
}

/** 목록 화면의 상태도 같이 바꾼다. */
function setStatus(session: Detail, status: Detail['status']) {
  session.status = status
  const summary = mockLearningSessions.find((s) => s.id === session.id)
  if (summary) summary.status = status
}

function refreshEvidence(session: Detail) {
  for (const unit of session.units) {
    unit.evidenceTurns = [...new Set(unit.items.flatMap((item) => item.sourceTurns))].sort((a, b) => a - b)
  }
}

export const mockSessionApi = {
  detail,

  setUnitExcluded(id: number, unitId: number, excluded: boolean): Detail {
    const unit = editable(id).units.find((u) => u.id === unitId)
    if (!unit) throw new ApiError(400, '복습 단위가 없어요.')
    unit.excluded = excluded
    for (const item of unit.items) {
      if (excluded) item.status = 'EXCLUDED'
      else if (item.sourceTurns.length > 0) item.status = 'NEW'
    }
    return detail(id)
  },

  setItemExcluded(id: number, itemId: number, excluded: boolean): Detail {
    const session = editable(id)
    const unit = session.units.find((u) => u.items.some((i) => i.id === itemId))
    const item = unit?.items.find((i) => i.id === itemId)
    if (!unit || !item) throw new ApiError(400, '기억 항목이 없어요.')
    if (!excluded && unit.excluded) throw new ApiError(409, '복습 단위를 먼저 다시 넣어 주세요.')
    if (!excluded && item.sourceTurns.length === 0) throw new ApiError(409, '근거 발화가 없어 다시 넣을 수 없어요.')
    item.status = excluded ? 'EXCLUDED' : 'NEW'
    return session
  },

  /** `meta`가 된 발화는 근거에서 빠지고, 근거가 없어진 항목은 제외된다(규칙 2, 15). */
  editTurn(id: number, index: number, content: Pick<Turn, 'text' | 'intent' | 'aiVerdict' | 'correction'>): Detail {
    const session = editable(id)
    const target = session.turns.find((t) => t.index === index)
    if (!target) throw new ApiError(400, '발화가 없어요.')
    Object.assign(target, content)
    if (content.intent === 'meta') {
      for (const item of session.units.flatMap((u) => u.items)) {
        item.sourceTurns = item.sourceTurns.filter((t) => t !== index)
        if (item.sourceTurns.length === 0) item.status = 'EXCLUDED'
      }
      refreshEvidence(session)
    }
    return session
  },

  /** `afterIndex` 뒤에 넣고 뒤 발화의 index를 1씩 민다. `sourceOf` 항목의 근거에 새 발화를 더한다. */
  insertTurn(id: number, afterIndex: number, content: Pick<Turn, 'text' | 'intent'>, sourceOf: number[]): Detail {
    const session = editable(id)
    const at = afterIndex + 1
    for (const t of session.turns) if (t.index >= at) t.index += 1
    for (const item of session.units.flatMap((u) => u.items)) {
      item.sourceTurns = item.sourceTurns.map((t) => (t >= at ? t + 1 : t))
      if (sourceOf.includes(item.id)) item.sourceTurns = [...item.sourceTurns, at].sort((a, b) => a - b)
    }
    session.turns.push(turn(at, content.text, content.intent))
    session.turns.sort((a, b) => a.index - b.index)
    refreshEvidence(session)
    return session
  },

  confirm(id: number): Detail {
    const session = editable(id)
    setStatus(session, 'CONFIRMED')
    session.confirmedAt = new Date().toISOString()
    return session
  },

  goals(id: number): Schemas['OptionsView'] {
    detail(id)
    return {
      available: GOALS,
      selected: chosen[id]?.goals ?? ['KEY_RECALL', 'CORRECT_MISCONCEPTION'],
      saved: id in chosen,
      maxSelected: 3,
      plan: composition(chosen[id]?.goals ?? ['KEY_RECALL', 'CORRECT_MISCONCEPTION']),
    }
  },

  strength(id: number): Schemas['Options'] {
    const itemCount = detail(id).units.flatMap((u) => u.items).filter((i) => i.status !== 'EXCLUDED').length
    return {
      current: chosen[id]?.strength ?? null,
      itemCount,
      simulationDays: 30,
      options: STRENGTHS.map((s) => ({ ...s, dailyMinutes: s.dailyMinutes * Math.max(1, itemCount / 4) })),
    }
  },

  /** 목표를 고르면 문제 생성이 시작되고, 몇 초 뒤 품질 검사를 통과한 문제가 준비된다. */
  chooseGoals(id: number, goals: Goal[], strength: Strength): Schemas['Composition'] {
    const session = detail(id)
    if (session.status !== 'CONFIRMED') throw new ApiError(409, '확인을 마친 뒤, 문제를 만들기 전에 고를 수 있어요.')
    chosen[id] = { goals, strength, at: Date.now() }
    return composition(goals)
  },

  firstStudy(id: number): Schemas['FirstStudy'] {
    const session = detail(id)
    const pick = chosen[id]
    if (pick && session.status === 'CONFIRMED' && Date.now() - pick.at > GENERATION_MS) setStatus(session, 'QUESTIONS_READY')
    if (session.status === 'CONFIRMED') {
      return { sessionStatus: session.status, generating: !!pick, failed: false, failureReason: null, planned: pick ? 7 : 0, questions: [], held: [] }
    }
    return {
      sessionStatus: session.status,
      generating: false,
      failed: false,
      failureReason: null,
      planned: 7,
      questions: mockFirstStudyQuestions,
      held: SAMPLE_HELD,
    }
  },
}

const GENERATION_MS = 4000

const chosen: Record<number, { goals: Goal[]; strength: Strength; at: number }> = {}

const GOALS: Schemas['GoalView'][] = [
  { goal: 'KEY_RECALL', number: 1, label: '핵심 내용 기억하기', description: '정의, 용어, 규칙을 단서 없이 떠올린다.' },
  { goal: 'PRINCIPLE', number: 2, label: '원리 이해하기', description: '결과가 발생하는 이유와 작동 과정을 이해한다.' },
  { goal: 'DISTINGUISH', number: 3, label: '개념 구분하기', description: '비슷한 개념의 차이를 구분한다.' },
  { goal: 'CONDITION', number: 4, label: '조건과 예외 판단하기', description: '어떤 조건에서 규칙이 적용되거나 달라지는지 판단한다.' },
  { goal: 'APPLY_CASE', number: 5, label: '사례에 적용하기', description: '배운 내용을 새로운 상황이나 문제에 적용한다.' },
  {
    goal: 'CORRECT_MISCONCEPTION',
    number: 6,
    label: '잘못된 이해 바로잡기',
    description: '이전에 가졌던 오개념이나 불완전한 이해를 교정한다.',
  },
  { goal: 'EXPLAIN_OWN_WORDS', number: 7, label: '자기 말로 설명하기', description: '외운 문장이 아니라 자신의 언어로 개념을 설명한다.' },
]

const STRENGTHS: Schemas['Option'][] = [
  { strength: 'LIGHT', label: '가볍게 기억', desiredRetention: 0.8, maxQuestionLevel: 2, reviewsInPeriod: 9, dailyMinutes: 0.3 },
  { strength: 'UNDERSTAND', label: '개념 이해', desiredRetention: 0.85, maxQuestionLevel: 2, reviewsInPeriod: 12, dailyMinutes: 0.4 },
  { strength: 'APPLY', label: '실무 적용', desiredRetention: 0.9, maxQuestionLevel: 3, reviewsInPeriod: 16, dailyMinutes: 0.6 },
  { strength: 'MASTER', label: '완전 숙달', desiredRetention: 0.95, maxQuestionLevel: 3, reviewsInPeriod: 25, dailyMinutes: 0.9 },
]

function composition(goals: Goal[]): Schemas['Composition'] {
  return {
    goals: goals.map((goal) => ({ goal, candidates: 3, planned: 2, reason: null })),
    maxQuestions: 7,
    questions: [],
    unplannedItemIds: [],
  }
}

const SAMPLE_HELD: Schemas['HeldSlot'][] = [{ position: 6, memoryItemId: 321, type: 'SHORT_ANSWER' }]
