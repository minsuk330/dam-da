import { router, type Href } from 'expo-router';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';

import { useStreak } from '@/api/daily';
import { useMemoryModel } from '@/api/memory-model';
import { useSession } from '@/api/session';
import { useDailySettings } from '@/api/settings';
import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { Icon, type IconName } from '@/components/icon';
import { ThemedText } from '@/components/themed-text';
import { dailySettingsStatus, memoryModelStatus } from '@/labels';
import { useProfile } from '@/profile';
import { colors, components, spacing } from '@/theme';

/**
 * 내 정보: 나에게 맞춰진 것들을 모아 둔 곳. 내 기억 패턴(망각 곡선·개인 모델)과 매일 학습 설정, 정책·회원 탈퇴로 간다.
 * 각 행은 자기 데이터를 불러오기 전·실패해도 설명 문구로 열 수 있게 둔다(눌러서 들어간 화면이 상태를 보여준다).
 */
export function Me() {
  const streak = useStreak();
  const model = useMemoryModel();
  const settings = useDailySettings();
  const { signOut } = useSession();
  const profile = useProfile();

  return (
    <ScrollView contentContainerStyle={styles.content}>
      <View style={styles.profile}>
        <View style={styles.avatar}>
          <ThemedText variant="display" tone="primaryInk">
            {profile.initial}
          </ThemedText>
        </View>
        <ThemedText variant="title">{profile.name}</ThemedText>
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

      <Card style={styles.list}>
        <Row icon="shield" title="개인정보 처리방침" status="어떤 정보를 받고 언제 지우는지" href="/privacy" />
        <Row icon="globe" title="개인정보 국외 이전" status="서버(홍콩)와 AI 처리(미국)" href="/consent/overseas" divided />
        <Row icon="file-text" title="이용약관" status="서비스 이용 조건" href="/terms" divided />
        <Row icon="user-x" title="회원 탈퇴" status="계정과 학습 기록을 모두 지워요" href="/account-deletion" divided />
      </Card>

      <Button variant="secondary" title="로그아웃" onPress={signOut} />
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
