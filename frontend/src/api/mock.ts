import type { Schemas } from './client'

/**
 * API를 연결하기 전까지 화면 전체를 확인하기 위한 mock 데이터. 기본으로 켜져 있고,
 * 실제 백엔드를 쓰려면 EXPO_PUBLIC_API_MOCK=false 로 실행한다.
 */
export const mockEnabled = process.env.EXPO_PUBLIC_API_MOCK !== 'false'

/** 불러오는 중 상태도 잠깐 보이도록 실제 요청처럼 늦게 돌려준다. */
export function mockResponse<T>(data: T, delayMs = 400): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(structuredClone(data)), delayMs))
}

type Detail = Schemas['ConversationDetailResponse']

const daysAgo = (days: number) => new Date(Date.now() - days * 24 * 60 * 60 * 1000).toISOString()

const details: Detail[] = [
  {
    id: 'mock-innodb',
    learningSessionId: 3,
    receivedAt: daysAgo(0),
    inputPath: 'connector',
    shareSource: null,
    fidelity: 'model_transcribed',
    topicHint: 'InnoDB 잠금과 MVCC',
    userTurns: [
      { index: 1, text: 'MVCC가 뭐야?', quotedText: null, intent: 'info_request', aiVerdict: 'not_applicable', correction: null },
      {
        index: 2,
        text: 'SELECT도 S 락 거는 거 맞지?',
        quotedText: null,
        intent: 'understanding_check',
        aiVerdict: 'corrected',
        correction: '일반 SELECT는 잠금 없이 스냅샷을 읽는다',
      },
      {
        index: 3,
        text: '그러니까 스냅샷을 읽는 거지?',
        quotedText: null,
        intent: 'understanding_check',
        aiVerdict: 'confirmed',
        correction: null,
      },
      { index: 4, text: '복습에 넣어줘', quotedText: null, intent: 'meta', aiVerdict: 'not_applicable', correction: null },
    ],
    reviewUnits: [
      {
        title: 'MVCC 읽기',
        evidenceTurns: [1, 2, 3],
        keyPoints: [
          { point: 'MVCC는 행의 여러 버전을 두고 트랜잭션마다 맞는 버전을 읽는다', turns: [1], kind: 'fact' },
          { point: '일반 SELECT는 잠금 없이 스냅샷을 읽는다', turns: [1, 3], kind: 'fact' },
        ],
        confusionPoints: [{ turn: 2, userBelief: '일반 SELECT도 S 락을 건다' }],
      },
      {
        title: '잠금 읽기',
        evidenceTurns: [2],
        keyPoints: [{ point: 'SELECT ... FOR UPDATE는 배타 락을 건다', turns: [2], kind: 'warning' }],
        confusionPoints: [],
      },
    ],
    warnings: [],
  },
  {
    id: 'mock-stats',
    learningSessionId: 2,
    receivedAt: daysAgo(2),
    inputPath: 'share_link',
    shareSource: 'chatgpt',
    fidelity: 'verbatim',
    topicHint: '경영통계: 표준오차',
    userTurns: [
      { index: 1, text: '표준오차가 뭐야?', quotedText: null, intent: 'info_request', aiVerdict: 'not_applicable', correction: null },
      {
        index: 2,
        text: '표준편차랑 같은 거 아니야?',
        quotedText: null,
        intent: 'understanding_check',
        aiVerdict: 'corrected',
        correction: '표준오차는 표본평균의 퍼짐이고, 표준편차는 개별 값의 퍼짐이다',
      },
      {
        index: 3,
        text: '그럼 표본이 커지면 표준오차는 줄어드는 거네?',
        quotedText: null,
        intent: 'restatement',
        aiVerdict: 'confirmed',
        correction: null,
      },
    ],
    reviewUnits: [
      {
        title: '표준오차',
        evidenceTurns: [1, 2, 3],
        keyPoints: [
          { point: '표준오차는 표본평균이 얼마나 퍼지는지 나타낸다', turns: [1], kind: 'fact' },
          { point: '표본 크기가 커지면 표준오차는 √n에 반비례해 줄어든다', turns: [3], kind: 'fact' },
        ],
        confusionPoints: [{ turn: 2, userBelief: '표준오차와 표준편차는 같다' }],
      },
    ],
    warnings: ['발화 2의 교정 내용이 원문과 다를 수 있어요.'],
  },
  {
    id: 'mock-react',
    learningSessionId: 1,
    receivedAt: daysAgo(5),
    inputPath: 'paste',
    shareSource: null,
    fidelity: 'verbatim',
    topicHint: 'React 재렌더링',
    userTurns: [
      {
        index: 1,
        text: '부모가 렌더링되면 자식도 항상 다시 그려져?',
        quotedText: null,
        intent: 'info_request',
        aiVerdict: 'not_applicable',
        correction: null,
      },
      {
        index: 2,
        text: 'memo로 감싸면 props가 같을 때 건너뛰는 거지?',
        quotedText: null,
        intent: 'understanding_check',
        aiVerdict: 'partial',
        correction: '얕은 비교라서 객체를 새로 만들어 넘기면 매번 다시 그린다',
      },
    ],
    reviewUnits: [
      {
        title: 'memo와 얕은 비교',
        evidenceTurns: [1, 2],
        keyPoints: [
          { point: '부모가 렌더링되면 자식도 기본으로 다시 렌더링된다', turns: [1], kind: 'fact' },
          { point: 'memo는 props를 얕게 비교하므로 인라인 객체는 매번 새 값이다', turns: [2], kind: 'practice' },
        ],
        confusionPoints: [],
      },
    ],
    warnings: [],
  },
]

/** API처럼 오래된 것부터. */
export const mockConversations: Schemas['ConversationSummaryResponse'][] = [...details].reverse().map((d) => ({
  id: d.id,
  learningSessionId: d.learningSessionId,
  receivedAt: d.receivedAt,
  inputPath: d.inputPath,
  shareSource: d.shareSource,
  fidelity: d.fidelity,
  topicHint: d.topicHint,
  userTurnCount: d.userTurns.length,
  reviewUnitCount: d.reviewUnits.length,
  warningCount: d.warnings.length,
}))

export function mockConversation(id: string): Detail | undefined {
  return details.find((d) => d.id === id)
}

/** API처럼 최근 것부터. 상태를 고루 두어 기억 탭의 칩·게이지 상태를 모두 볼 수 있게 한다. */
export const mockLearningSessions: Schemas['LearningSessionSummary'][] = [
  {
    id: 3,
    conversationId: 'mock-innodb',
    status: 'AWAITING_CONFIRMATION',
    topicHint: 'InnoDB 잠금과 MVCC',
    inputPath: 'connector',
    fidelity: 'model_transcribed',
    field: { code: 'cs.db', label: '데이터베이스', fieldCode: 'cs', fieldLabel: '컴퓨터·IT', source: 'AUTO' },
    createdAt: daysAgo(0),
    unitCount: 2,
    itemCount: 4,
  },
  {
    id: 2,
    conversationId: 'mock-stats',
    status: 'QUESTIONS_READY',
    topicHint: '경영통계: 표준오차',
    inputPath: 'share_link',
    fidelity: 'verbatim',
    field: { code: 'math.stats', label: '확률·통계', fieldCode: 'math', fieldLabel: '수학·통계', source: 'AUTO' },
    createdAt: daysAgo(2),
    unitCount: 1,
    itemCount: 3,
  },
  {
    id: 1,
    conversationId: 'mock-react',
    status: 'IN_PROGRESS',
    topicHint: 'React 재렌더링',
    inputPath: 'paste',
    fidelity: 'model_transcribed',
    field: { code: 'cs.frontend', label: '프론트엔드·모바일', fieldCode: 'cs', fieldLabel: '컴퓨터·IT', source: 'USER' },
    createdAt: daysAgo(5),
    unitCount: 1,
    itemCount: 2,
  },
]

/**
 * 알림 시각이 지난 "오늘의 학습"과 검수를 마친 세션의 "학습 내용 도착" 알림.
 * 읽음 처리를 mock 안에서도 유지하도록 변경 가능한 객체로 둔다.
 */
export const mockNotifications: Schemas['NotificationsView'] = {
  unreadCount: 2,
  items: [
    {
      id: 3,
      type: 'DAILY_LEARNING',
      title: '오늘의 학습 · 약 5분',
      body: '문제 3개를 풀면 오늘 학습이 끝나요.',
      // 시작 전에 보낸 매일 학습 알림은 풀이 ID가 없다(계약 보정은 navigation.ts dailyPracticeOf).
      targetId: null as unknown as number,
      read: false,
      createdAt: daysAgo(0),
    },
    {
      id: 2,
      type: 'SESSION_READY',
      title: 'InnoDB 잠금과 MVCC 학습 내용이 도착했어요',
      body: '공부할 내용을 확인해 보세요.',
      targetId: 3,
      read: false,
      createdAt: daysAgo(0),
    },
    {
      id: 1,
      type: 'SESSION_READY',
      title: '경영통계: 표준오차 학습 내용이 도착했어요',
      body: '공부할 내용을 확인해 보세요.',
      targetId: 2,
      read: true,
      createdAt: daysAgo(2),
    },
  ],
}

export const mockIntake: Schemas['IntakeResponse'] = {
  conversationId: 'mock-innodb',
  learningSessionId: 3,
  inputPath: 'paste',
  userTurnCount: 4,
  reviewUnitCount: 2,
  warnings: [],
}
