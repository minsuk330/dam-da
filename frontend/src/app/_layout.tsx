import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useFonts } from 'expo-font';
import { Stack } from 'expo-router';
import * as SplashScreen from 'expo-splash-screen';
import { StatusBar } from 'expo-status-bar';
import { useEffect } from 'react';

import { ApiError } from '@/api/client';
import { SessionProvider, useSession } from '@/api/session';
import { PhoneFrame } from '@/components/phone-frame';
import { MockBadge } from '@/screens/mock-badge';
import { colors, fonts, typography } from '@/theme';
import { inToss } from '@/toss';
import { TossBridge } from '@/toss-bridge';

SplashScreen.preventAutoHideAsync();

// 4xx는 다시 불러도 같으므로 재시도하지 않는다.
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: (failureCount, error) => !(error instanceof ApiError && error.status < 500) && failureCount < 1,
    },
  },
});

export default function RootLayout() {
  const [loaded, error] = useFonts(fonts);

  useEffect(() => {
    if (loaded || error) SplashScreen.hideAsync();
  }, [loaded, error]);

  if (!loaded && !error) return null;

  return (
    <QueryClientProvider client={queryClient}>
      <SessionProvider>
        <PhoneFrame>
          <StatusBar style="dark" />
          <MockBadge />
          <TossBridge />
          <AppStack />
        </PhoneFrame>
      </SessionProvider>
    </QueryClientProvider>
  );
}

/** 로그인 전에는 메인(로그인) 화면만, 로그인 뒤에는 앱 화면만 열린다 (스펙 §7.9). */
function AppStack() {
  const { signedIn } = useSession();
  return (
    <Stack
      screenOptions={{
        headerShadowVisible: false,
        headerTitleAlign: 'center',
        headerStyle: { backgroundColor: colors.canvas },
        headerTintColor: colors.ink,
        headerTitleStyle: { fontFamily: typography.headline.fontFamily, fontSize: typography.headline.fontSize },
        contentStyle: { backgroundColor: colors.canvas },
        // 토스 인앱(#149)은 토스 내비게이션 바의 뒤로가기만 쓴다(자체 뒤로가기와 동시 노출 금지, TossBridge가 처리).
        ...(inToss && { headerBackVisible: false, headerLeft: () => null }),
      }}>
      <Stack.Protected guard={!signedIn}>
        <Stack.Screen name="welcome" options={{ headerShown: false }} />
      </Stack.Protected>
      <Stack.Protected guard={signedIn}>
        <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
        <Stack.Screen name="conversations/index" options={{ title: '받은 학습 대화' }} />
        <Stack.Screen name="conversations/[id]" options={{ title: '대화 확인' }} />
        <Stack.Screen name="sessions/[id]/index" options={{ title: '학습 내용 확인' }} />
        <Stack.Screen name="sessions/[id]/memory" options={{ title: '기억 상태' }} />
        <Stack.Screen name="memory-model" options={{ title: '내 기억 패턴' }} />
        <Stack.Screen name="notifications" options={{ title: '알림' }} />
        <Stack.Screen name="me" options={{ title: '내 정보' }} />
        <Stack.Screen name="settings" options={{ title: '매일 학습 설정' }} />
        <Stack.Screen name="review" options={{ title: '오늘의 복습' }} />
      </Stack.Protected>
      {/* 서버 로그인에서 돌아오는 곳. 로그인 전후 모두 열려 있어야 한다. 보호된 화면에서 밀려날 때 첫 화면으로 고르지 않도록 맨 뒤에 둔다. */}
      <Stack.Screen name="auth/callback" options={{ headerShown: false }} />
      {/* 정책·탈퇴 안내는 로그인 전후 모두 열린다. 스토어 심사용 웹 공개 주소이기도 하다(#148). */}
      <Stack.Screen name="privacy" options={{ title: '개인정보 처리방침' }} />
      <Stack.Screen name="terms" options={{ title: '이용약관' }} />
      {/* 토스 로그인 동의문(#149). 앱인토스 콘솔에 공개 주소로 등록한다. */}
      <Stack.Screen name="consent/privacy" options={{ title: '개인정보 수집·이용 동의' }} />
      <Stack.Screen name="consent/overseas" options={{ title: '개인정보 국외 이전 동의' }} />
      <Stack.Screen name="account-deletion" options={{ title: '회원 탈퇴' }} />
    </Stack>
  );
}
