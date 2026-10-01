import { ApiError, type Schemas } from './client'
import type { Feedback, Next } from './practice'

// 풀이(practice 컨텍스트)의 mock. 서버 규칙을 단순하게 흉내 낸다.
// - 다음 문제: 지금 제시에 답하지 않았으면 같은 제시를 다시 준다.
// - 판정: 객관식은 정답 번호로, 그 밖에는 핵심어가 모두 들어 있으면 충족(실제로는 Jev). 너무 짧은 답은 판정 신뢰도 미달로 보류한다.
// - 단계적 피드백(첫 학습): 오답 → 힌트 → 재도전 → 개념 설명(확인 문제를 큐 끝에) → 다음. 확인 문제는 한 번뿐이다.

type Presentation = Schemas['PresentationView']
type Attempt = Schemas['AttemptView']
type Submission = Schemas['Submission']
type QuestionView = Schemas['QuestionView']

type MockQuestion = QuestionView & {
  answerIndex?: number
  keywords?: string[][]
  hint: string
  explanation: string
  evidenceTurns: number[]
}

const QUESTIONS: MockQuestion[] = [
  {
    questionId: 901,
    position: 0,
    memoryItemId: 312,
    learningGoal: 'KEY_RECALL',
    type: 'MULTIPLE_CHOICE',
    stem: 'InnoDB에서 잠금 없이 스냅샷을 읽는 것은 어느 쪽일까요?',
    choices: ['일반 SELECT', 'SELECT ... FOR UPDATE', 'SELECT ... FOR SHARE', 'UPDATE'],
    answerIndex: 0,
    hint: 'FOR가 붙은 SELECT는 잠금 읽기예요.',
    explanation: '일반 SELECT는 MVCC 덕분에 잠금 없이 트랜잭션 시작 시점의 스냅샷을 읽어요. FOR UPDATE·FOR SHARE는 최신 버전에 락을 거는 잠금 읽기예요.',
    evidenceTurns: [2, 3],
  },
  {
    questionId: 902,
    position: 1,
    memoryItemId: 313,
    learningGoal: 'CORRECT_MISCONCEPTION',
    type: 'ERROR_FINDING',
    stem: '한 학습자가 이렇게 설명했어요. "InnoDB에서 일반 SELECT는 읽는 행에 S 락을 걸고 읽는다." 어디가 틀렸고, 바르게 고치면 무엇일까요?',
    choices: [],
    keywords: [['잠금', '락'], ['스냅샷', '버전']],
    hint: 'AI가 대화에서 "잠금 없이"라는 말을 했어요. 일반 SELECT가 무엇을 읽는지 떠올려 보세요.',
    explanation: '대화에서는 일반 SELECT도 S 락을 건다고 생각했어요. 실제로는 잠금 없이 스냅샷을 읽고, S 락은 SELECT ... FOR SHARE 같은 잠금 읽기에서 걸려요.',
    evidenceTurns: [2],
  },
  {
    questionId: 903,
    position: 2,
    memoryItemId: 311,
    learningGoal: 'PRINCIPLE',
    type: 'SHORT_ANSWER',
    stem: 'MVCC에서 트랜잭션마다 맞는 데이터를 읽을 수 있도록 행마다 여러 개 두는 것은 무엇일까요?',
    choices: [],
    keywords: [['버전']],
    hint: '같은 행을 시점별로 여러 벌 둔다고 생각해 보세요.',
    explanation: 'MVCC는 행의 여러 버전을 두고, 트랜잭션마다 시작 시점에 맞는 버전을 읽게 해요.',
    evidenceTurns: [1],
  },
  {
    questionId: 904,
    position: 3,
    memoryItemId: 321,
    learningGoal: 'CONDITION',
    type: 'CASE_JUDGMENT',
    stem: '재고를 읽은 뒤 그 값으로 바로 차감하려고 해요. 다른 트랜잭션이 그사이 값을 바꾸지 못하게 하려면 일반 SELECT와 SELECT ... FOR UPDATE 중 무엇을 써야 할까요? 이유도 써 주세요.',
    choices: [],
    keywords: [['for update'], ['락', '잠금']],
    hint: '읽은 행을 다른 트랜잭션이 못 바꾸게 하려면 무엇이 필요할까요?',
    explanation: '일반 SELECT는 잠금 없이 스냅샷을 읽어서 그사이 다른 트랜잭션이 값을 바꿀 수 있어요. FOR UPDATE는 읽은 행에 배타 락을 걸어 막아요.',
    evidenceTurns: [4],
  },
  {
    questionId: 905,
    position: 4,
    memoryItemId: 312,
    learningGoal: 'EXPLAIN_OWN_WORDS',
    type: 'ESSAY',
    stem: '일반 SELECT가 다른 트랜잭션의 쓰기를 기다리지 않는 이유를 내 말로 설명해 보세요.',
    choices: [],
    keywords: [['스냅샷', '버전'], ['잠금', '락']],
    hint: '읽는 쪽이 최신 행 대신 무엇을 읽는지 떠올려 보세요.',
    explanation: '일반 SELECT는 잠금을 걸지 않고 스냅샷(이전 버전)을 읽어요. 그래서 쓰기 트랜잭션이 잡은 락과 부딪히지 않아요.',
    evidenceTurns: [1, 3],
  },
  {
    questionId: 906,
    position: 5,
    memoryItemId: 311,
    learningGoal: 'APPLY_CASE',
    type: 'CASE_APPLICATION',
    stem: '긴 보고서 조회 트랜잭션이 도는 동안 다른 사용자가 주문을 계속 수정해요. 보고서 트랜잭션 안의 일반 SELECT는 어떤 값을 읽게 될까요?',
    choices: [],
    keywords: [['시작', '처음', '스냅샷']],
    hint: '트랜잭션이 시작될 때 무엇이 정해지는지 떠올려 보세요.',
    explanation: '보고서 트랜잭션은 시작 시점의 스냅샷을 계속 읽으므로, 그 뒤에 바뀐 주문은 보이지 않아요.',
    evidenceTurns: [1],
  },
]

/** 문제 준비 화면이 보여 줄 첫 학습 문제(품질 검사 통과분). */
export const mockFirstStudyQuestions: QuestionView[] = QUESTIONS.map(
  ({ questionId, position, memoryItemId, learningGoal, type, stem, choices }) => ({
    questionId,
    position,
    memoryItemId,
    learningGoal,
    type,
    stem,
    choices,
  }),
)

/** 매일 학습 큐(객관식·단답·사례 판단). */
const DAILY_QUESTIONS = [901, 903, 904]

type Outcome = 'CORRECT' | 'WRONG' | 'UNCERTAIN'

type PresentationState = {
  view: Presentation
  question: MockQuestion
  recheckOf: number | null
  outcomes: Outcome[]
  hintShown: boolean
  explanationShown: boolean
  /** 마지막 시도 뒤에 힌트·설명을 보여 줘 재도전을 기다리는가. */
  aidAfterLatest: boolean
  relearnQueued: boolean
  feedback: Feedback
}

type Practice = {
  id: number
  kind: Schemas['PracticeView']['kind']
  /** 매일 학습은 세션에 묶이지 않는다. */
  sessionId: number | null
  queue: { questionId: number; recheckOf: number | null }[]
  presentations: PresentationState[]
  completed: boolean
}

const practices: Record<number, Practice> = {}
let nextId = 1

const normalize = (text: string) => text.replace(/\s+/g, '').toLowerCase()

function view(p: Practice): Schemas['PracticeView'] {
  return { practiceId: p.id, kind: p.kind, learningSessionId: p.sessionId, total: p.queue.length, completed: p.completed }
}

function practiceOf(id: number): Practice {
  const practice = practices[id]
  if (!practice) throw new ApiError(404, '풀이를 찾을 수 없어요.')
  return practice
}

function current(presentationId: number): { practice: Practice; state: PresentationState } {
  for (const practice of Object.values(practices)) {
    const state = practice.presentations.find((s) => s.view.presentationId === presentationId)
    if (!state) continue
    if (practice.presentations.at(-1) !== state) throw new ApiError(409, '이미 지나간 문제예요.')
    return { practice, state }
  }
  throw new ApiError(404, '문제를 찾을 수 없어요.')
}

function emptyFeedback(presentationId: number): Feedback {
  return {
    presentationId,
    action: null,
    decidedBy: null,
    reason: null,
    path: null,
    hint: null,
    explanation: null,
    evidenceTurns: [],
    recheckQueued: false,
    prerequisite: null,
  }
}

function judge(question: MockQuestion, submission: Submission): Outcome {
  if (question.type === 'MULTIPLE_CHOICE') return submission.choiceIndex === question.answerIndex ? 'CORRECT' : 'WRONG'
  const text = normalize(submission.answer ?? '')
  if (text.length < 4) return 'UNCERTAIN'
  return (question.keywords ?? []).every((group) => group.some((k) => text.includes(normalize(k)))) ? 'CORRECT' : 'WRONG'
}

export const mockPracticeApi = {
  startFirstStudy(sessionId: number): Schemas['PracticeView'] {
    const existing = Object.values(practices).find((p) => p.sessionId === sessionId)
    if (existing) return view(existing)
    const practice: Practice = {
      id: nextId++,
      kind: 'FIRST_STUDY',
      sessionId,
      queue: QUESTIONS.map((q) => ({ questionId: q.questionId, recheckOf: null })),
      presentations: [],
      completed: false,
    }
    practices[practice.id] = practice
    return view(practice)
  },

  /** 오늘의 매일 학습 풀이. 이미 시작했으면 그 풀이를 준다. 복습 2문제 + 새 항목 1문제를 흉내 낸다. */
  startDaily(): Schemas['PracticeView'] {
    const existing = Object.values(practices).find((p) => p.kind === 'DAILY')
    if (existing) return view(existing)
    const practice: Practice = {
      id: nextId++,
      kind: 'DAILY',
      sessionId: null,
      queue: DAILY_QUESTIONS.map((questionId) => ({ questionId, recheckOf: null })),
      presentations: [],
      completed: false,
    }
    practices[practice.id] = practice
    return view(practice)
  },

  /** 매일 학습 풀이(시작했으면). */
  daily(): Schemas['PracticeView'] | null {
    const existing = Object.values(practices).find((p) => p.kind === 'DAILY')
    return existing ? view(existing) : null
  },

  next(practiceId: number): Next {
    const practice = practiceOf(practiceId)
    const last = practice.presentations.at(-1)
    if (last && last.outcomes.length === 0) return { done: false, presentation: last.view }
    const position = practice.presentations.length
    const entry = practice.queue[position]
    if (!entry) {
      practice.completed = true
      return { done: true, presentation: null }
    }
    const question = QUESTIONS.find((q) => q.questionId === entry.questionId)!
    const presentationId = practiceId * 1000 + position
    const state: PresentationState = {
      view: {
        presentationId,
        position,
        total: practice.queue.length,
        questionId: question.questionId,
        type: question.type,
        stem: question.stem,
        choices: question.choices,
        sameDayRecheck: entry.recheckOf !== null,
      },
      question,
      recheckOf: entry.recheckOf,
      outcomes: [],
      hintShown: false,
      explanationShown: false,
      aidAfterLatest: false,
      relearnQueued: false,
      feedback: emptyFeedback(presentationId),
    }
    practice.presentations.push(state)
    return { done: false, presentation: state.view }
  },

  submit(presentationId: number, submission: Submission): Attempt {
    const { state } = current(presentationId)
    const evaluated = state.outcomes.length === 0
    if (evaluated && !submission.selfAssessment) throw new ApiError(400, '자기평가를 고르세요.')
    const outcome = judge(state.question, submission)
    state.outcomes.push(outcome)
    state.aidAfterLatest = false
    const multipleChoice = state.question.type === 'MULTIPLE_CHOICE'
    const held = outcome === 'UNCERTAIN'
    return {
      attemptId: Date.now(),
      kind: state.recheckOf !== null ? 'DELAYED_RECHECK' : evaluated ? 'FIRST_UNASSISTED' : 'ASSISTED_RETRY',
      evaluated,
      correct: multipleChoice ? outcome === 'CORRECT' : null,
      responseTimeMs: submission.responseTimeMs ?? 0,
      judgment: {
        judged: !held,
        verdict: held ? null : outcome === 'CORRECT' ? 'MET' : 'NOT_MET',
        reason: outcome === 'WRONG' && !multipleChoice ? 'OMISSION' : null,
        misconceptionRecurred: false,
      },
      rating: !evaluated || held ? null : outcome === 'CORRECT' ? (submission.selfAssessment === 'RECALLED_EASILY' ? 'GOOD' : 'HARD') : 'AGAIN',
      holdReason: evaluated && held ? 'LOW_CONFIDENCE' : null,
      outcome,
    }
  },

  feedback(presentationId: number): Feedback {
    const { practice, state } = current(presentationId)
    const latest = state.outcomes.at(-1)
    if (!latest) throw new ApiError(409, '답한 뒤에 피드백을 받을 수 있어요.')
    const fb = state.feedback
    const recheck = state.recheckOf !== null
    let action: NonNullable<Feedback['action']>
    if (latest === 'UNCERTAIN') action = 'REQUEST_CONFIRMATION'
    else if (latest === 'CORRECT' || recheck || state.explanationShown) action = 'ADVANCE'
    else if (state.aidAfterLatest) action = 'RETRY'
    // 매일 학습은 힌트·확인 문제 대신 그날 큐 끝에 한 번 더 낸다.
    else if (practice.kind === 'DAILY') action = state.relearnQueued ? 'EXPLAIN_CONCEPT' : 'RELEARN_TODAY'
    else action = state.hintShown ? 'EXPLAIN_CONCEPT' : 'GIVE_HINT'

    if (action === 'RELEARN_TODAY') {
      practice.queue.push({ questionId: state.question.questionId, recheckOf: presentationId })
      state.relearnQueued = true
      fb.recheckQueued = true
    } else if (action === 'GIVE_HINT') {
      state.hintShown = true
      state.aidAfterLatest = true
      fb.hint = state.question.hint
    } else if (action === 'EXPLAIN_CONCEPT') {
      state.explanationShown = true
      state.aidAfterLatest = true
      fb.explanation = state.question.explanation
      fb.evidenceTurns = state.question.evidenceTurns
      if (!state.relearnQueued && practice.kind === 'FIRST_STUDY') {
        practice.queue.push({ questionId: state.question.questionId, recheckOf: presentationId })
        state.relearnQueued = true
        fb.recheckQueued = true
      }
    }
    const wrongs = state.outcomes.filter((o) => o === 'WRONG').length
    fb.path =
      latest === 'CORRECT'
        ? state.explanationShown
          ? 'AFTER_EXPLANATION'
          : state.hintShown
            ? 'AFTER_HINT'
            : 'INDEPENDENT'
        : wrongs >= 2
          ? 'REPEATED_WRONG'
          : null
    fb.action = action
    fb.decidedBy = 'RULE'
    return fb
  },
}
