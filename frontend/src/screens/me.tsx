import { useQueryClient } from '@tanstack/react-query';
import { router, type Href } from 'expo-router';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';

import { useStreak } from '@/api/daily';
import { useMemoryModel } from '@/api/memory-model';
import { authGateEnabled, useSession } from '@/api/session';
import { useDailySettings } from '@/api/settings';
import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { Icon, type IconName } from '@/components/icon';
import { ThemedText } from '@/components/themed-text';
import { dailySettingsStatus, memoryModelStatus } from '@/labels';
import { colors, components, spacing } from '@/theme';

/**
 * 내 정보: 나에게 맞춰진 것들을 모아 둔 곳. 내 기억 패턴(망각 곡선·개인 모델)과 매일 학습 설정으로 간다.
 * 각 행은 자기 데이터를 불러오기 전·실패해도 설명 문구로 열 수 있게 둔다(눌러서 들어간 화면이 상태를 보여준다).
 */
export function Me() {
  const streak = useStreak();
  const model = useMemoryModel();
  const settings = useDailySettings();
  const { signOut } = useSession();
  const queryClient = useQueryClient();

  /** 다른 사람이 같은 기기로 로그인해도 이전 계정의 화면이 남지 않게 불러온 데이터를 지운다. */
  function logOut() {
    queryClient.clear();
    signOut();
  }

  return (
    <ScrollView contentContainerStyle={styles.content}>
      <View style={styles.profile}>
        <View style={styles.avatar}>
          <ThemedText variant="display" tone="primaryInk">
            지
          </ThemedText>
        </View>
        <ThemedText variant="title">지원</ThemedText>
        {streak.data && (
          <ThemedText variant="subhead" tone="inkSecondary">
            연속 학습 {streak.data.current}일 · 최고 {streak.data.best}일
          </ThemedText>
        )}
      </View>

      <Card style={styles.list}>
        <Row icon="activity" title="내 기억 패턴" status={memoryModelStatus(model.data)} href="/memory-model" />
        <Row
          icon="clock"
          title="매일 학습 설정"
          status={dailySettingsStatus(settings.data)}
          href="/settings"
          divided
        />
      </Card>

      {/* 로그인 게이트가 켜진 빌드에서만. 실제 API는 #95(앱 토큰) 전이라 데모 사용자 하나로 동작한다. */}
      {authGateEnabled && <Button variant="secondary" title="로그아웃" onPress={logOut} />}
    </ScrollView>
  );
}

function Row({
  icon,
  title,
  status,
  href,
  divided,
}: {
  icon: IconName;
  title: string;
  status: string;
  href: Href;
  divided?: boolean;
}) {
  return (
    <Pressable
      accessibilityRole="link"
      accessibilityLabel={`${title}, ${status}`}
      onPress={() => router.push(href)}
      style={({ pressed }) => [styles.row, divided && styles.divided, pressed && styles.rowPressed]}>
      <View style={styles.rowIcon}>
        <Icon name={icon} color={colors.primaryInk} />
      </View>
      <View style={styles.rowText}>
        <ThemedText variant="headline">{title}</ThemedText>
        <ThemedText variant="caption" tone="inkSecondary">
          {status}
        </ThemedText>
      </View>
      <Icon name="chevron-right" color={colors.inkMuted} />
    </Pressable>
  );
}

const styles = StyleSheet.create({
  content: { padding: spacing.xl, gap: spacing['2xl'] },
  profile: { alignItems: 'center', gap: spacing.xs, paddingTop: spacing.lg },
  // 프로필 큰 원은 완료 표시(done-mark)와 같은 크기를 쓴다.
  avatar: {
    width: components.doneMark.size,
    height: components.doneMark.size,
    borderRadius: components.doneMark.rounded,
    backgroundColor: colors.primaryTint,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: spacing.sm,
  },
  list: { paddingVertical: spacing.xs },
  row: { flexDirection: 'row', alignItems: 'center', gap: spacing.md, paddingVertical: spacing.lg },
  divided: { borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.outline },
  rowPressed: { backgroundColor: components.cardPressed.backgroundColor },
  rowIcon: {
    width: components.iconCircle.size,
    height: components.iconCircle.size,
    borderRadius: components.iconCircle.rounded,
    backgroundColor: components.iconCircle.backgroundColor,
    alignItems: 'center',
    justifyContent: 'center',
  },
  rowText: { flex: 1, gap: spacing['2xs'] },
});
