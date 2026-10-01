import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, StyleSheet, View } from 'react-native';

import { ApiError } from '@/api/client';
import { exchangeCode, useSession } from '@/api/session';
import { Button } from '@/components/button';
import { Notice } from '@/components/notice';
import { ThemedText } from '@/components/themed-text';
import { colors, spacing } from '@/theme';

/**
 * 서버 소셜 로그인이 돌려보내는 곳(`/auth/callback?code=…`, 실패면 `?error=…`). 1회용 코드를 앱 토큰으로 바꾸고 홈으로 간다.
 * 코드는 한 번만 쓸 수 있어 바꾸기를 한 번만 시도한다.
 */
export function AuthCallback() {
  const { code, error } = useLocalSearchParams<{ code?: string; error?: string }>();
  const { complete } = useSession();
  const [failure, setFailure] = useState<string | null>(
    error
      ? '로그인하지 못했어요. 다시 시도해 주세요.'
      : !code
        ? '로그인 정보가 없어요. 처음 화면에서 다시 시작해 주세요.'
        : null,
  );
  const started = useRef(false);

  useEffect(() => {
    if (started.current || error || !code) return;
    started.current = true;
    exchangeCode(code)
      .then((token) => {
        complete(token);
        router.replace('/');
      })
      .catch((e) =>
        setFailure(
          e instanceof ApiError && e.status === 401
            ? '로그인 시간이 지났어요. 처음 화면에서 다시 시작해 주세요.'
            : '로그인을 마치지 못했어요. 다시 시도해 주세요.',
        ),
      );
  }, [code, error, complete]);

  return (
    <View style={styles.screen}>
      {failure ? (
        <View style={styles.failure}>
          <Notice tone="danger">{failure}</Notice>
          <Button title="처음 화면으로" onPress={() => router.replace('/welcome')} />
        </View>
      ) : (
        <View style={styles.pending}>
          <ActivityIndicator color={colors.primary} />
          <ThemedText variant="body" tone="inkSecondary">
            로그인하는 중이에요
          </ThemedText>
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.canvas, justifyContent: 'center', padding: spacing.xl },
  pending: { alignItems: 'center', gap: spacing.md },
  failure: { gap: spacing.md },
});
