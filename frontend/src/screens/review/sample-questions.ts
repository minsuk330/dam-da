// 화면 설계용 예시 문제. 문제 생성·품질 검사(Jev) API가 생기면 이 파일을 지우고 API로 바꾼다.
// 실제 문제는 Jev 품질 검사를 통과한 것만 노출한다(AGENTS.md 학습 도메인 규칙).

export type SampleQuestion = {
  id: string;
  /** 문제가 묻는 것: 대화 신호에서 온 출제 이유를 라벨로 보여준다. */
  kind: '핵심 내용' | '헷갈렸던 점' | '개념 확인';
  unitTitle: string;
  prompt: string;
  options: string[];
  answerIndex: number;
  explanation: string;
  evidenceTurn: number;
};

export const sampleQuestions: SampleQuestion[] = [
  {
    id: 'se-1',
    kind: '헷갈렸던 점',
    unitTitle: '표준오차',
    prompt: '표본 크기가 커지면 줄어드는 것은 무엇일까요?',
    options: ['표준편차', '표준오차', '표본평균', '모평균'],
    answerIndex: 1,
    explanation: '표준편차는 데이터 자체의 퍼짐이라 표본이 커져도 그대로예요. 줄어드는 건 표본평균의 퍼짐인 표준오차예요.',
    evidenceTurn: 2,
  },
  {
    id: 'se-2',
    kind: '개념 확인',
    unitTitle: '표준오차',
    prompt: '표본 크기가 4배가 되면 표준오차는 어떻게 될까요?',
    options: ['그대로', '절반', '2배', '4분의 1'],
    answerIndex: 1,
    explanation: '표준오차는 σ/√n이라 n이 4배면 √4 = 2로 나뉘어 절반이 돼요.',
    evidenceTurn: 1,
  },
  {
    id: 'se-3',
    kind: '핵심 내용',
    unitTitle: '표준오차',
    prompt: '표준오차가 나타내는 것은 무엇일까요?',
    options: ['개별 값의 퍼짐', '표본평균의 퍼짐', '측정 실수 횟수', '모평균의 크기'],
    answerIndex: 1,
    explanation: '표준오차는 같은 크기의 표본을 여러 번 뽑았을 때 표본평균이 얼마나 흩어지는지를 나타내요.',
    evidenceTurn: 1,
  },
];

/** 홈에 보여줄 예상 소요 시간. 문제당 약 1.5분으로 어림한다. */
export const sampleMinutes = Math.ceil(sampleQuestions.length * 1.5);
