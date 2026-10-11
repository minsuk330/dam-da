import { ActivityIndicator, StyleSheet, View } from 'react-native';

import { useSession } from '@/api/session';
import { Button } from '@/components/button';
import { Logo } from '@/components/logo';
import { ThemedText } from '@/components/themed-text';
import { colors, components, spacing } from '@/theme';
import { inToss } from '@/toss';

import { SocialWelcome } from './social';

/**
 * 로그인 전 화면. 웹은 소셜 로그인 화면, 토스 인앱(#149)은 로그인 없이 들어가는 동안의 준비 화면이다
 * (노출 정책: 로그인 전에 주요 기능을 써볼 수 있어야 함. 서비스 소개는 홈의 첫 사용자 화면이 맡는다).
 */
export function Welcome() {
  return inToss ? <TossEntry /> : <SocialWelcome />;
}

/** 토스 인앱 자동 진입(익명 식별키) 중이거나 실패했을 때. 성공하면 보호된 화면 전환으로 홈이 열린다. */
function TossEntry() {
  const { entry, retryEntry } = useSession();
  return (
    <View style={[styles.screen, styles.entry]}>
      <Logo size={components.iconButtonPrimary.size} />
      {entry === 'error' ? (
        <>
          <ThemedText variant="title">담다를 열지 못했어요</ThemedText>
          <ThemedText variant="subhead" tone="inkSecondary" style={styles.note}>
            잠시 후 다시 시도해 주세요.
          </ThemedText>
          <Button variant="secondary" title="다시 시도" onPress={retryEntry} />
        </>
      ) : (
        <>
          <ActivityIndicator color={colors.primary} />
          <ThemedText variant="subhead" tone="inkSecondary">
            담다를 여는 중이에요
          </ThemedText>
        </>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.canvas },
  entry: { alignItems: 'center', justifyContent: 'center', gap: spacing.lg, padding: spacing.xl },
  note: { textAlign: 'center' },
});
