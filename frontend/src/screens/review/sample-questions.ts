// 화면 설계용 예시 문제와 예시 채점. 풀이·판정 API(이슈 #16, #17, #20, #21)가 생기면 이 파일을 지우고 API로 바꾼다.
// 실제 문제는 Jev 품질 검사를 통과한 것만 노출하고, 서술·단답 판정은 Jev가 한다(AGENTS.md 학습 도메인 규칙).

export type QuestionType = 'MULTIPLE_CHOICE' | 'SHORT_ANSWER' | 'ESSAY' | 'ERROR_FINDING';

type Base = {
  id: string;
  /** 출제 이유: 대화 신호에서 온 것을 라벨로 보여준다. */
  kind: '핵심 내용' | '헷갈렸던 점' | '개념 확인';
  unitTitle: string;
  prompt: string;
  /** 첫 오답 뒤에 보여줄 힌트. 대화 속 AI 교정과 핵심 사실에서 만든다(스펙 §7 5단계). */
  hint: string;
  /** 정답을 한 문장으로. 개념 설명에서 보여준다. */
  answerText: string;
  explanation: string;
  /** 사용자가 대화에서 믿었던 내용. 있으면 개념 설명에서 나란히 비교한다. */
  userBelief?: string;
  evidenceTurn: number;
  /** 개념 설명 뒤 몇 문제 지나서 묻는 확인 문제(스펙 §6.4.8). */
  followUp?: Question;
};

export type Question = Base &
  (
    | { type: 'MULTIPLE_CHOICE'; choices: string[]; answerIndex: number }
    | { type: 'SHORT_ANSWER'; accepted: string[] }
    | { type: 'ESSAY'; /** 그룹마다 하나 이상 들어가야 한다(예시 채점). */ keywordGroups: string[][] }
    | { type: 'ERROR_FINDING'; segments: string[]; wrongIndex: number }
  );

export type Answer =
  | { type: 'MULTIPLE_CHOICE'; index: number | null }
  | { type: 'SHORT_ANSWER' | 'ESSAY'; text: string }
  | { type: 'ERROR_FINDING'; index: number | null; correction: string };

export function emptyAnswer(question: Question): Answer {
  switch (question.type) {
    case 'MULTIPLE_CHOICE':
      return { type: 'MULTIPLE_CHOICE', index: null };
    case 'ERROR_FINDING':
      return { type: 'ERROR_FINDING', index: null, correction: '' };
    default:
      return { type: question.type, text: '' };
  }
}

export function isAnswered(answer: Answer): boolean {
  switch (answer.type) {
    case 'MULTIPLE_CHOICE':
    case 'ERROR_FINDING':
      return answer.index !== null;
    default:
      return answer.text.trim().length > 0;
  }
}

const normalize = (text: string) => text.replace(/\s+/g, '').toLowerCase();

/** 예시 채점. 객관식·오류 찾기는 코드가, 단답·서술은 실제로는 Jev가 판정한다. */
export function gradeSample(question: Question, answer: Answer): boolean {
  if (question.type === 'MULTIPLE_CHOICE' && answer.type === 'MULTIPLE_CHOICE') return answer.index === question.answerIndex;
  if (question.type === 'ERROR_FINDING' && answer.type === 'ERROR_FINDING') return answer.index === question.wrongIndex;
  if (question.type === 'SHORT_ANSWER' && answer.type === 'SHORT_ANSWER') {
    return question.accepted.some((a) => normalize(answer.text).includes(normalize(a)));
  }
  if (question.type === 'ESSAY' && answer.type === 'ESSAY') {
    const text = normalize(answer.text);
    return question.keywordGroups.every((group) => group.some((k) => text.includes(normalize(k))));
  }
  return false;
}

export const sampleQuestions: Question[] = [
  {
    id: 'se-mc',
    type: 'MULTIPLE_CHOICE',
    kind: '헷갈렸던 점',
    unitTitle: '표준오차',
    prompt: '표본 크기가 커지면 줄어드는 것은 무엇일까요?',
    choices: ['표준편차', '표준오차', '표본평균', '모평균'],
    answerIndex: 1,
    hint: '데이터 하나하나가 아니라, 표본평균이 얼마나 흔들리는지를 떠올려 보세요.',
    answerText: '표본 크기가 커지면 표준오차가 줄어든다.',
    userBelief: '표준오차와 표준편차는 같다',
    explanation: '표준편차는 데이터 자체의 퍼짐이라 표본이 커져도 그대로예요. 줄어드는 건 표본평균의 퍼짐인 표준오차예요.',
    evidenceTurn: 2,
    followUp: {
      id: 'se-mc-check',
      type: 'MULTIPLE_CHOICE',
      kind: '개념 확인',
      unitTitle: '표준오차',
      prompt: '표본을 많이 뽑아도 그대로인 것은 무엇일까요?',
      choices: ['표준오차', '표본평균의 퍼짐', '표준편차', '신뢰구간 폭'],
      answerIndex: 2,
      hint: '데이터 자체의 성질은 표본 크기와 상관이 없어요.',
      answerText: '표준편차는 표본 크기와 상관없이 데이터 자체의 퍼짐이다.',
      explanation: '표준편차는 모집단 데이터의 퍼짐이라 표본 크기에 따라 달라지지 않아요.',
      evidenceTurn: 2,
    },
  },
  {
    id: 'se-short',
    type: 'SHORT_ANSWER',
    kind: '개념 확인',
    unitTitle: '표준오차',
    prompt: '표본 크기가 4배가 되면 표준오차는 몇 배가 될까요?',
    accepted: ['절반', '1/2', '0.5', '반'],
    hint: '표준오차는 σ/√n이에요. n이 4배면 √n은 몇 배일까요?',
    answerText: '표본이 4배가 되면 표준오차는 절반이 된다.',
    explanation: 'n이 4배면 √n이 2배가 되어 σ/√n은 절반이 돼요.',
    evidenceTurn: 3,
  },
  {
    id: 'mvcc-error',
    type: 'ERROR_FINDING',
    kind: '헷갈렸던 점',
    unitTitle: 'MVCC 읽기',
    prompt: '대화에서 이렇게 생각했어요. 틀린 부분을 고르고 바르게 고쳐 보세요.',
    segments: ['InnoDB에서', '일반 SELECT는', '행에 S 락을 걸고 읽는다'],
    wrongIndex: 2,
    hint: 'AI가 대화에서 "잠금 없이"라는 말을 했어요. 일반 SELECT가 무엇을 읽는지 떠올려 보세요.',
    answerText: '일반 SELECT는 잠금 없이 스냅샷을 읽는다.',
    userBelief: '일반 SELECT도 S 락을 건다',
    explanation: 'MVCC 덕분에 일반 SELECT는 잠금을 걸지 않고 트랜잭션 시작 시점의 스냅샷을 읽어요. S 락은 SELECT ... FOR SHARE처럼 잠금 읽기를 할 때 걸려요.',
    evidenceTurn: 2,
    followUp: {
      id: 'mvcc-check',
      type: 'MULTIPLE_CHOICE',
      kind: '개념 확인',
      unitTitle: 'MVCC 읽기',
      prompt: '잠금 없이 스냅샷을 읽는 것은 어느 쪽일까요?',
      choices: ['일반 SELECT', 'SELECT ... FOR UPDATE', 'SELECT ... FOR SHARE', 'UPDATE'],
      answerIndex: 0,
      hint: 'FOR가 붙은 SELECT는 잠금 읽기예요.',
      answerText: '일반 SELECT는 잠금 없이 스냅샷을 읽는다.',
      explanation: 'FOR UPDATE·FOR SHARE는 잠금 읽기라 최신 버전에 락을 걸어요.',
      evidenceTurn: 3,
    },
  },
  {
    id: 'se-essay',
    type: 'ESSAY',
    kind: '핵심 내용',
    unitTitle: '표준오차',
    prompt: '표준오차와 표준편차의 차이를 내 말로 설명해 보세요.',
    keywordGroups: [
      ['표본평균', '평균'],
      ['개별', '데이터', '값'],
    ],
    hint: '하나는 "개별 값"의 퍼짐, 다른 하나는 "무엇"의 퍼짐일까요?',
    answerText: '표준편차는 개별 값의 퍼짐, 표준오차는 표본평균의 퍼짐이다.',
    userBelief: '표준오차와 표준편차는 같다',
    explanation: '표준편차는 데이터 하나하나가 평균에서 얼마나 떨어져 있는지, 표준오차는 표본을 여러 번 뽑을 때 표본평균이 얼마나 흔들리는지를 나타내요.',
    evidenceTurn: 2,
  },
  {
    id: 'react-mc',
    type: 'MULTIPLE_CHOICE',
    kind: '핵심 내용',
    unitTitle: 'memo와 얕은 비교',
    prompt: 'memo로 감싼 자식에 매번 다시 그려지게 만드는 props는?',
    choices: ['숫자 상수', '문자열 상수', '인라인 객체 {}', 'useMemo로 만든 객체'],
    answerIndex: 2,
    hint: 'memo는 props를 얕게 비교해요. 렌더링마다 새로 만들어지는 값은 무엇일까요?',
    answerText: '렌더링마다 새로 만드는 인라인 객체는 매번 다른 값이라 memo가 건너뛰지 못한다.',
    explanation: '인라인 객체는 렌더링마다 새 참조가 되어 얕은 비교에서 항상 달라요.',
    evidenceTurn: 2,
  },
];

/** 홈에 보여줄 예상 소요 시간. 문제당 약 1.5분으로 어림한다. */
export const sampleMinutes = Math.ceil(sampleQuestions.length * 1.5);
