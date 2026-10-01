import { Link, router } from 'expo-router';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { useConversations } from '@/api/conversations';
import { useLearningSessions } from '@/api/learning-sessions';
import { useMarkNotificationRead, useNotifications } from '@/api/notifications';
import { Card } from '@/components/card';
import { Chip } from '@/components/chip';
import { Gauge, percent } from '@/components/gauge';
import { Icon } from '@/components/icon';
import { useTabBarSpace } from '@/components/tab-bar';
import { ThemedText } from '@/components/themed-text';
import { formatDateTime } from '@/labels';
import { sampleAverage, sampleWeakest } from '@/screens/memory/sample-gauges';
import { colors, components, spacing } from '@/theme';

import { sampleStreak, sampleToday } from './sample-today';
import { StatCard } from './stat-card';

const today = new Intl.DateTimeFormat('ko-KR', { month: 'long', day: 'numeric', weekday: 'long' });

/** 홈: 새 세션 알림 → 오늘의 학습 → 연속 학습 일수 → 기억 게이지 요약 → 최근 받은 대화 (스펙 §6.4.3, §7.7). */
export function Home() {
  const insets = useSafeAreaInsets();
  const tabBarSpace = useTabBarSpace();
  const totalCount = sampleToday.reviewCount + sampleToday.newCount;

  return (
    <ScrollView
      style={styles.screen}
      contentContainerStyle={[styles.content, { paddingTop: insets.top + spacing.lg, paddingBottom: tabBarSpace }]}>
      <HomeHeader />

      <SessionReadyBanner />

      <View style={styles.titleRow}>
        <View style={styles.title}>
          <ThemedText variant="caption" tone="inkMuted">
            {today.format(new Date())} · 지원님
          </ThemedText>
          <ThemedText variant="display">오늘 학습할{'\n'}지식이 있어요</ThemedText>
        </View>
        <Chip variant="status" label={`${totalCount}문제`} style={styles.titleChip} />
      </View>

      <View style={styles.stats}>
        <StatCard
          variant="lavender"
          icon="clock"
          value={String(sampleToday.minutes)}
          unit="분"
          label="오늘 예상 학습 시간"
        />
        <StatCard
          variant="surface"
          icon="zap"
          value={String(sampleStreak.days)}
          unit="일"
          label={sampleStreak.studiedToday ? '연속 학습 중' : `오늘 하면 ${sampleStreak.days + 1}일째`}
        />
      </View>

      <Pressable
        accessibilityRole="button"
        accessibilityLabel="오늘의 학습 시작하기"
        onPress={() => router.push('/review')}>
        {({ pressed }) => (
          <Card variant="hero" pressed={pressed} style={styles.hero}>
            <View style={styles.heroText}>
              <ThemedText variant="title" tone="onPrimary">
                오늘의 학습{'\n'}시작하기
              </ThemedText>
              <ThemedText variant="subhead" tone="onPrimary">
                복습 {sampleToday.reviewCount} · 새 항목 {sampleToday.newCount} · 약 {sampleToday.minutes}분
              </ThemedText>
            </View>
            <View style={styles.heroArrow}>
              <Icon name="arrow-up-right" size="lg" color={colors.primary} />
            </View>
          </Card>
        )}
      </Pressable>

      <MemorySummary />

      <RecentConversations />
    </ScrollView>
  );
}

/**
 * 레퍼런스 홈 헤더: 왼쪽 원형 메뉴 버튼 + 서비스명, 오른쪽 원형 알림 버튼 + 프로필.
 * 메뉴 화면이 없어 메뉴는 받은 학습 대화 목록으로, 알림은 알림 목록(#61) 전까지 읽지 않은 최신 세션으로 보낸다.
 */
function HomeHeader() {
  const { data } = useNotifications();
  const unread = data?.items.find((n) => !n.read && n.type === 'SESSION_READY');

  return (
    <View style={styles.header}>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="메뉴: 받은 학습 대화"
        onPress={() => router.push('/conversations')}
        style={({ pressed }) => [styles.circleButton, pressed && styles.circlePressed]}>
        <Icon name="menu" />
      </Pressable>
      <ThemedText variant="headline" numberOfLines={1} style={styles.brand}>
        AI Learning Companion
      </ThemedText>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={unread ? '알림, 새 알림 있음' : '알림'}
        onPress={() =>
          unread
            ? router.push({ pathname: '/sessions/[id]', params: { id: String(unread.targetId) } })
            : router.navigate('/memory')
        }
        style={({ pressed }) => [styles.circleButton, pressed && styles.circlePressed]}>
        <Icon name="bell" />
        {unread && <View style={styles.unreadDot} />}
      </Pressable>
      <View accessibilityLabel="프로필: 지원" style={styles.avatar}>
        <ThemedText variant="headline" tone="primaryInk">
          지
        </ThemedText>
      </View>
    </View>
  );
}

/** 검수를 마친 새 세션의 "학습 내용 도착" 알림. 읽지 않은 것 중 최신 하나만 보여준다. */
function SessionReadyBanner() {
  const { data } = useNotifications();
  const markRead = useMarkNotificationRead();
  const latest = data?.items.find((n) => !n.read && n.type === 'SESSION_READY');
  // 불러오는 중·오류·없음은 배너를 그리지 않는다. 알림은 홈의 보조 정보라 자리를 비워 두지 않는다.
  if (!latest) return null;

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={`${latest.title}. ${latest.body}`}
      onPress={() => {
        markRead.mutate(latest.id);
        router.push({ pathname: '/sessions/[id]', params: { id: String(latest.targetId) } });
      }}
      style={({ pressed }) => [styles.row, styles.banner, pressed && styles.rowPressed]}>
      <View style={[styles.rowIcon, styles.bannerIcon]}>
        <Icon name="bell" size="md" color={colors.onPrimary} />
      </View>
      <View style={styles.rowText}>
        <ThemedText variant="headline" numberOfLines={2}>
          {latest.title}
        </ThemedText>
        <ThemedText variant="caption" tone="inkMuted">
          {latest.body}
        </ThemedText>
      </View>
      <Icon name="chevron-right" size="md" color={colors.inkMuted} />
    </Pressable>
  );
}

/** 기억 게이지 요약: 확인된 세션의 평균과 가장 약한 세션. 값은 게이지 API(이슈 #21) 전까지 예시다. */
function MemorySummary() {
  const { data, isPending, isError } = useLearningSessions();
  const weakest = data ? sampleWeakest(data) : null;

  return (
    <View style={styles.section}>
      <View style={styles.sectionHeader}>
        <ThemedText variant="title">내 기억</ThemedText>
        <Link href="/memory" asChild>
          <Pressable accessibilityRole="link" hitSlop={8}>
            <ThemedText variant="subhead" tone="primaryInk">
              자세히
            </ThemedText>
          </Pressable>
        </Link>
      </View>
      <Card style={styles.memoryCard}>
        {isPending && <ThemedText tone="inkMuted">불러오는 중…</ThemedText>}
        {isError && <ThemedText tone="dangerInk">기억 상태를 불러오지 못했어요.</ThemedText>}
        {data?.length === 0 && <ThemedText tone="inkMuted">대화를 추가하면 얼마나 기억하는지 보여드려요.</ThemedText>}
        {data && data.length > 0 && (
          <>
            <Gauge size="lg" value={sampleAverage(data)} />
            {weakest && (
              <View style={styles.weakest}>
                <Chip label="가장 약함" />
                <ThemedText variant="subhead" tone="inkSecondary" numberOfLines={1} style={styles.weakestText}>
                  {weakest.session.topicHint ?? '주제 없음'} {percent(weakest.value)}%
                </ThemedText>
              </View>
            )}
            <ThemedText variant="caption" tone="inkMuted">
              예시 값이에요. 복습하면 다시 올라가요.
            </ThemedText>
          </>
        )}
      </Card>
    </View>
  );
}

function RecentConversations() {
  const { data: conversations, isPending, isError } = useConversations();
  const recent = conversations ? [...conversations].reverse().slice(0, 3) : [];

  return (
    <View style={styles.section}>
      <View style={styles.sectionHeader}>
        <ThemedText variant="title">최근 받은 대화</ThemedText>
        <Link href="/conversations" asChild>
          <Pressable accessibilityRole="link" hitSlop={8}>
            <ThemedText variant="subhead" tone="primaryInk">
              전체 보기
            </ThemedText>
          </Pressable>
        </Link>
      </View>

      {isPending && <ThemedText tone="inkMuted">불러오는 중…</ThemedText>}
      {isError && <ThemedText tone="dangerInk">대화를 불러오지 못했어요.</ThemedText>}
      {conversations?.length === 0 && (
        <ThemedText tone="inkMuted">아직 받은 대화가 없어요. Claude에서 “복습에 넣어줘”라고 요청해 보세요.</ThemedText>
      )}
      {recent.map((c) => (
        <Pressable
          key={c.id}
          accessibilityRole="link"
          onPress={() => router.push({ pathname: '/conversations/[id]', params: { id: c.id } })}
          style={({ pressed }) => [styles.row, pressed && styles.rowPressed]}>
          <View style={styles.rowIcon}>
            <Icon name="book-open" size="md" color={colors.primaryInk} />
          </View>
          <View style={styles.rowText}>
            <ThemedText variant="headline" numberOfLines={1}>
              {c.topicHint ?? '주제 없음'}
            </ThemedText>
            <ThemedText variant="caption" tone="inkMuted">
              {formatDateTime(c.receivedAt)} · 복습 단위 {c.reviewUnitCount}개
            </ThemedText>
          </View>
          {c.warningCount > 0 && <View accessibilityLabel={`경고 ${c.warningCount}`} style={styles.warningDot} />}
          <Icon name="chevron-right" size="md" color={colors.inkMuted} />
        </Pressable>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.canvas },
  content: { paddingHorizontal: spacing.xl, gap: spacing['2xl'] },
  header: { flexDirection: 'row', alignItems: 'center', gap: spacing.xs },
  brand: { flex: 1, marginLeft: spacing.sm },
  circleButton: {
    width: components.iconButton.size,
    height: components.iconButton.size,
    borderRadius: components.iconButton.rounded,
    backgroundColor: components.iconButton.backgroundColor,
    alignItems: 'center',
    justifyContent: 'center',
  },
  circlePressed: { backgroundColor: components.cardPressed.backgroundColor },
  unreadDot: {
    position: 'absolute',
    top: spacing.md,
    right: spacing.md,
    width: components.warningMark.size,
    height: components.warningMark.size,
    borderRadius: components.warningMark.size,
    backgroundColor: colors.danger,
  },
  avatar: {
    width: components.iconButton.size,
    height: components.iconButton.size,
    borderRadius: components.iconButton.rounded,
    backgroundColor: colors.primaryTint,
    alignItems: 'center',
    justifyContent: 'center',
  },
  titleRow: { flexDirection: 'row', alignItems: 'flex-start', justifyContent: 'space-between', gap: spacing.md },
  title: { flex: 1, gap: spacing.xs },
  titleChip: { marginTop: spacing.sm },
  stats: { flexDirection: 'row', gap: spacing.md },
  hero: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    justifyContent: 'space-between',
    minHeight: components.cardHero.height,
  },
  heroText: { gap: spacing.sm, flex: 1 },
  heroArrow: {
    width: components.iconButton.size,
    height: components.iconButton.size,
    borderRadius: components.iconButton.rounded,
    backgroundColor: colors.surface,
    alignItems: 'center',
    justifyContent: 'center',
  },
  section: { gap: spacing.md },
  sectionHeader: { flexDirection: 'row', alignItems: 'baseline', justifyContent: 'space-between' },
  memoryCard: { gap: spacing.md },
  weakest: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm },
  weakestText: { flex: 1 },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
    backgroundColor: colors.surface,
    borderRadius: components.card.rounded,
    borderCurve: 'continuous',
    padding: spacing.lg,
  },
  rowPressed: { backgroundColor: components.cardPressed.backgroundColor },
  banner: { borderWidth: components.answerOptionOutline.width, borderColor: colors.primaryTint },
  rowIcon: {
    width: components.iconCircle.size,
    height: components.iconCircle.size,
    borderRadius: components.iconCircle.rounded,
    backgroundColor: components.iconCircle.backgroundColor,
    alignItems: 'center',
    justifyContent: 'center',
  },
  bannerIcon: { backgroundColor: colors.primary },
  rowText: { flex: 1, gap: spacing['2xs'] },
  warningDot: {
    width: components.warningMark.size,
    height: components.warningMark.size,
    borderRadius: components.warningMark.size,
    backgroundColor: components.warningMark.backgroundColor,
  },
});
