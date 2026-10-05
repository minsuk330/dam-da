import { router } from 'expo-router';
import { useEffect } from 'react';

import { inToss } from '@/toss';

/**
 * 토스 인앱(#149)에서만 하는 화면 밖 처리. 비게임 출시 가이드 기준:
 * - 뒤로가기(토스 내비게이션 바·안드로이드 백버튼)는 앱 화면 기록을 따라가고, 첫 화면에서는 미니앱을 닫는다.
 *   backEvent를 구독하면 토스의 기본 뒤로가기가 막히므로 여기서 직접 처리한다. 앱 헤더의 뒤로가기는 _layout에서 숨긴다.
 * - 지도처럼 꼭 필요한 곳 외에는 손가락 확대·축소를 막는다. 지식 그래프는 자체 확대를 쓴다.
 * 웹 빌드에서는 아무것도 하지 않는다.
 */
export function TossBridge() {
  useEffect(() => {
    if (!inToss) return;
    document
      .querySelector('meta[name="viewport"]')
      ?.setAttribute('content', 'width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no, viewport-fit=cover');

    let unsubscribe: (() => void) | undefined;
    let cancelled = false;
    // 토스 SDK는 토스 빌드에서만 쓰므로 일반 웹 번들에 섞이지 않게 필요할 때 불러온다.
    import('@apps-in-toss/web-framework').then(({ graniteEvent, closeView }) => {
      if (cancelled) return;
      unsubscribe = graniteEvent.addEventListener('backEvent', {
        onEvent: () => {
          if (router.canGoBack()) router.back();
          else void closeView();
        },
        onError: () => {
          // 이벤트를 못 받으면 토스 기본 동작에 맡긴다.
        },
      });
    });
    return () => {
      cancelled = true;
      unsubscribe?.();
    };
  }, []);
  return null;
}
