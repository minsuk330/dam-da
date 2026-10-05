import type { ImageSourcePropType } from 'react-native';

import type { Schemas } from '@/api/client';
import type { IconName } from '@/components/icon';

export type ShareSource = NonNullable<Schemas['ConversationDetailResponse']['shareSource']>;

/** 설명 한 조각. `strong`은 사용자가 눌러야 할 버튼·메뉴 이름이라 굵게 보여준다. */
export type DetailPart = string | { strong: string };

export type GuideStep = {
  title: string;
  detail: DetailPart[];
  /** 캡처가 없을 때 캡처 자리에 크게 보여줄 아이콘. */
  icon: IconName;
  /** 그 단계의 실제 화면 캡처(#168)와 원본 가로/세로 비율. 캡처가 없으면 아이콘으로 대신한다. */
  image?: { source: ImageSourcePropType; aspectRatio: number };
};

export type ShareGuide = {
  source: ShareSource;
  label: string;
  /** 어디서 하는 안내인지. 토스 사용자는 휴대폰이라 앱 기준으로 쓰고, Codex는 PC 웹 기준이다. */
  where: string;
  /** 단계 화면의 "열기" 링크. */
  openUrl: string;
  steps: GuideStep[];
  /** 담다에 붙여넣을 링크 모양. 맞는 링크를 복사했는지 사용자가 비교한다. */
  linkExample: string;
};

const pasteStep: GuideStep = {
  title: '담다에 붙여넣기',
  detail: ['담다 ', { strong: '추가' }, ' 탭의 공유 링크 칸에 붙여넣어요. 이런 모양이면 맞아요.'],
  icon: 'clipboard',
};

// 버튼 이름·위치는 각 서비스 화면이 바뀌면 달라진다. 캡처를 받을 때 실제 화면과 맞춘다(#168).
export const SHARE_GUIDES: ShareGuide[] = [
  {
    source: 'chatgpt',
    label: 'ChatGPT',
    where: 'ChatGPT 앱',
    openUrl: 'https://chatgpt.com',
    // 2026-10-05 ChatGPT iOS 앱 캡처 기준.
    steps: [
      {
        title: '더보기 누르기',
        detail: ['담을 대화를 열고 오른쪽 위 ', { strong: '⋯' }, ' 버튼을 눌러요.'],
        icon: 'more-horizontal',
        image: { source: require('@/assets/share-guide/chatgpt-1-more.png'), aspectRatio: 1206 / 645 },
      },
      {
        title: '공유 누르기',
        detail: ['메뉴 맨 위의 ', { strong: '공유' }, '를 눌러요.'],
        icon: 'share',
        image: { source: require('@/assets/share-guide/chatgpt-2-share.png'), aspectRatio: 1206 / 1060 },
      },
      {
        title: '링크 공유 누르기',
        detail: ['아래쪽 ', { strong: '링크 공유' }, '를 누른 뒤 ', { strong: '복사' }, '를 골라요.'],
        icon: 'copy',
        image: { source: require('@/assets/share-guide/chatgpt-3-link.png'), aspectRatio: 1206 / 550 },
      },
      pasteStep,
    ],
    linkExample: 'https://chatgpt.com/share/...',
  },
  {
    source: 'claude',
    label: 'Claude',
    where: 'Claude 앱',
    openUrl: 'https://claude.ai',
    steps: [
      {
        title: '담을 대화 열기',
        detail: ['Claude에서 복습하고 싶은 내용을 나눈 ', { strong: '대화' }, '를 열어요.'],
        icon: 'message-square',
      },
      {
        title: '공유 누르기',
        detail: ['대화 제목이나 ', { strong: '⋯' }, ' 메뉴에서 ', { strong: '공유' }, '를 눌러요.'],
        icon: 'share',
      },
      {
        title: '공개 링크 복사하기',
        detail: [
          { strong: '공개 링크 만들기' },
          '로 공유를 켠 뒤 ',
          { strong: '링크 복사' },
          '를 눌러요. 공유를 켜지 않은 링크는 담을 수 없어요.',
        ],
        icon: 'copy',
      },
      pasteStep,
    ],
    linkExample: 'https://claude.ai/share/...',
  },
  {
    source: 'codex',
    label: 'Codex',
    where: 'PC 웹의 Codex',
    openUrl: 'https://chatgpt.com/codex',
    steps: [
      {
        title: '담을 작업 열기',
        detail: ['PC에서 ', { strong: 'chatgpt.com/codex' }, '를 열고 복습하고 싶은 ', { strong: '작업' }, '을 열어요.'],
        icon: 'monitor',
      },
      {
        title: '공유 누르기',
        detail: ['작업 화면 오른쪽 위 ', { strong: '공유' }, ' 버튼을 눌러요.'],
        icon: 'share',
      },
      {
        title: '링크 복사하기',
        detail: [{ strong: '링크 복사' }, '를 눌러요.'],
        icon: 'copy',
      },
      pasteStep,
    ],
    linkExample: 'https://chatgpt.com/s/cx_...',
  },
];

export function findGuide(source: string | undefined): ShareGuide | undefined {
  return SHARE_GUIDES.find((g) => g.source === source);
}

/** 입력한 링크로 어느 서비스 안내를 보여줄지 고른다. 모르면 null. */
export function guessShareSource(url: string): ShareSource | null {
  const value = url.trim().toLowerCase();
  if (value.includes('claude.ai')) return 'claude';
  if (value.includes('/s/cx_')) return 'codex';
  if (value.includes('chatgpt.com') || value.includes('chat.openai.com')) return 'chatgpt';
  return null;
}
