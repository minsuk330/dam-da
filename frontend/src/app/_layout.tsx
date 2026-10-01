import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useFonts } from 'expo-font';
import { Stack } from 'expo-router';
import * as SplashScreen from 'expo-splash-screen';
import { StatusBar } from 'expo-status-bar';
import { useEffect } from 'react';

import { ApiError } from '@/api/client';
import { PhoneFrame } from '@/components/phone-frame';
import { MockBadge } from '@/screens/mock-badge';
import { colors, fonts, typography } from '@/theme';

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
      <PhoneFrame>
        <StatusBar style="dark" />
        <MockBadge />
        <Stack
          screenOptions={{
            headerShadowVisible: false,
            headerTitleAlign: 'center',
            headerStyle: { backgroundColor: colors.canvas },
            headerTintColor: colors.ink,
            headerTitleStyle: { fontFamily: typography.headline.fontFamily, fontSize: typography.headline.fontSize },
            contentStyle: { backgroundColor: colors.canvas },
          }}>
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
        </Stack>
      </PhoneFrame>
    </QueryClientProvider>
  );
}
