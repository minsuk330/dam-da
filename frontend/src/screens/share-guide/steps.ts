import type { ImageSourcePropType } from 'react-native';

import type { Schemas } from '@/api/client';

export type ShareSource = NonNullable<Schemas['ConversationDetailResponse']['shareSource']>;

export type GuideStep = {
  title: string;
  detail: string;
  /** 그 단계의 실제 화면 캡처(#168). 받기 전에는 비워 두고 글로만 안내한다. */
  image?: ImageSourcePropType;
};

export type ShareGuide = {
  source: ShareSource;
  label: string;
  /** 어디서 하는 안내인지. 토스 사용자는 휴대폰이라 앱 기준으로 쓰고, Codex는 PC 웹 기준이다. */
  where: string;
  steps: GuideStep[];
  /** 담다에 붙여넣을 링크 모양. 맞는 링크를 복사했는지 사용자가 비교한다. */
  linkExample: string;
  note?: string;
};

// 버튼 이름·위치는 각 서비스 화면이 바뀌면 달라진다. 캡처를 받을 때 실제 화면과 맞춘다(#168).
export const SHARE_GUIDES: ShareGuide[] = [
  {
    source: 'chatgpt',
    label: 'ChatGPT',
    where: 'ChatGPT 앱',
    steps: [
      { title: '담을 대화를 열어요', detail: '복습하고 싶은 내용을 나눈 대화를 열어요.' },
      { title: '공유를 눌러요', detail: '화면 오른쪽 위의 공유 버튼을 눌러요. 안 보이면 ⋯ 메뉴 안에 있어요.' },
      { title: '링크를 복사해요', detail: '링크 만들기를 누른 뒤 링크 복사를 눌러요.' },
    ],
    linkExample: 'https://chatgpt.com/share/...',
  },
  {
    source: 'claude',
    label: 'Claude',
    where: 'Claude 앱',
    steps: [
      { title: '담을 대화를 열어요', detail: '복습하고 싶은 내용을 나눈 대화를 열어요.' },
      { title: '공유를 눌러요', detail: '화면 위쪽 대화 제목이나 ⋯ 메뉴에서 공유를 눌러요.' },
      { title: '공개 링크를 만들고 복사해요', detail: '공개 링크 만들기를 눌러 공유를 켠 뒤 링크를 복사해요.' },
    ],
    linkExample: 'https://claude.ai/share/...',
    note: '공유를 켜지 않은 링크는 담을 수 없어요.',
  },
  {
    source: 'codex',
    label: 'Codex',
    where: 'PC 웹의 Codex',
    steps: [
      { title: '담을 작업을 열어요', detail: 'chatgpt.com/codex에서 복습하고 싶은 작업을 열어요.' },
      { title: '공유를 눌러요', detail: '작업 화면 오른쪽 위의 공유 버튼을 눌러요.' },
      { title: '링크를 복사해요', detail: '링크 복사를 눌러요.' },
    ],
    linkExample: 'https://chatgpt.com/s/cx_...',
  },
];

/** 입력한 링크로 어느 서비스 안내를 먼저 보여줄지 고른다. 모르면 null. */
export function guessShareSource(url: string): ShareSource | null {
  const value = url.trim().toLowerCase();
  if (value.includes('claude.ai')) return 'claude';
  if (value.includes('/s/cx_')) return 'codex';
  if (value.includes('chatgpt.com') || value.includes('chat.openai.com')) return 'chatgpt';
  return null;
}
