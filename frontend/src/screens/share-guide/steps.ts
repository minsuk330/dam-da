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
  /** 어디서 하는 안내인지. 토스 사용자는 휴대폰이라 앱 기준으로 쓴다. */
  where: string;
  /** 단계 화면의 "열기" 링크. */
  openUrl: string;
  steps: GuideStep[];
};

/**
 * 마지막 단계: 담다 추가 탭에 붙여넣기. 캡처는 담다 화면을 서비스별 링크 모양으로 직접 찍었다(링크 ID는 지어낸 값).
 * 사용자는 캡처 속 링크 모양과 복사한 링크를 비교한다.
 */
function pasteStep(image: ImageSourcePropType): GuideStep {
  return {
    title: '담다에 붙여넣기',
    detail: ['담다 ', { strong: '추가' }, ' 탭의 공유 링크 칸에 붙여넣어요. 이런 모양이면 맞아요.'],
    icon: 'clipboard',
    image: { source: image, aspectRatio: 1206 / 579 },
  };
}

// 버튼 이름·위치는 각 서비스 화면이 바뀌면 달라진다(#168). Codex 사용자는 공유 링크를 만들 줄 안다고 보고 안내하지 않는다.
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
        detail: ['아래쪽 ', { strong: '링크 공유' }, '를 누른 뒤 ', { strong: '링크 복사' }, '를 눌러요.'],
        icon: 'copy',
        image: { source: require('@/assets/share-guide/chatgpt-3-link.png'), aspectRatio: 1206 / 550 },
      },
      pasteStep(require('@/assets/share-guide/chatgpt-4-paste.png')),
    ],
  },
  {
    source: 'claude',
    label: 'Claude',
    where: 'Claude 앱',
    openUrl: 'https://claude.ai',
    // 2026-10-05 Claude iOS 앱 캡처 기준. 3번 캡처에 ③④ 두 동작이 있어 같은 캡처로 두 단계를 보여준다.
    steps: [
      {
        title: '더보기 누르기',
        detail: ['담을 대화를 열고 오른쪽 위 ', { strong: '⋯' }, ' 버튼을 눌러요.'],
        icon: 'more-horizontal',
        image: { source: require('@/assets/share-guide/claude-1-more.png'), aspectRatio: 1206 / 645 },
      },
      {
        title: '공유 누르기',
        detail: ['메뉴에서 ', { strong: '공유' }, '를 눌러요.'],
        icon: 'share',
        image: { source: require('@/assets/share-guide/claude-2-share.png'), aspectRatio: 1206 / 855 },
      },
      {
        title: '링크로 공개하기',
        detail: [
          '액세스 권한에서 ',
          { strong: '링크가 있는 모든 사용자' },
          '를 골라요. 나만 접근 가능으로 두면 담을 수 없어요.',
        ],
        icon: 'globe',
        image: { source: require('@/assets/share-guide/claude-3-link.png'), aspectRatio: 1206 / 1305 },
      },
      {
        title: '링크 공유 누르기',
        detail: ['아래쪽 ', { strong: '링크 공유' }, '를 누른 뒤 ', { strong: '링크 복사' }, '를 눌러요.'],
        icon: 'copy',
        image: { source: require('@/assets/share-guide/claude-3-link.png'), aspectRatio: 1206 / 1305 },
      },
      pasteStep(require('@/assets/share-guide/claude-5-paste.png')),
    ],
  },
];

export function findGuide(source: string | undefined): ShareGuide | undefined {
  return SHARE_GUIDES.find((g) => g.source === source);
}

/** 입력한 링크로 어느 서비스 안내를 보여줄지 고른다. 안내가 없는 서비스(Codex)나 모르는 링크면 null. */
export function guessShareSource(url: string): ShareSource | null {
  const value = url.trim().toLowerCase();
  if (value.includes('claude.ai')) return 'claude';
  if (value.includes('/s/cx_')) return null;
  if (value.includes('chatgpt.com') || value.includes('chat.openai.com')) return 'chatgpt';
  return null;
}
