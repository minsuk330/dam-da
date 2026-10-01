import { Link, router } from 'expo-router';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { useConversations } from '@/api/conversations';
import { minutesOf, streakLabel, useDaily, useStartDaily, useStreak, type Daily } from '@/api/daily';
import { useMemoryOverview } from '@/api/memory';
import { useMarkNotificationRead, useNotifications } from '@/api/notifications';
import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { Chip } from '@/components/chip';
import { Gauge, percent } from '@/components/gauge';
import { Icon } from '@/components/icon';
import { Notice } from '@/components/notice';
import { Skeleton, SkeletonList } from '@/components/skeleton';
import { useTabBarSpace } from '@/components/tab-bar';
import { ThemedText } from '@/components/themed-text';
import { formatRelativeDay, inputPathIcon } from '@/labels';
import { openNotification } from '@/navigation';
import { useProfile } from '@/profile';
import { colors, components, spacing } from '@/theme';

import { StatCard } from '@/components/stat-card';

const today = new Intl.DateTimeFormat('ko-KR', { month: 'long', day: 'numeric', weekday: 'long' });

const openPractice = (practiceId: number) =>
  router.push({ pathname: '/review', params: { practiceId: String(practiceId) } });

/**
 * 홈 (스펙 §6.4.3, §7.7): 새 세션 알림 → 인사와 오늘 상태 → 오늘의 학습(행동) → 연속 학습·오늘 문제 → 기억 요약 → 최근 받은 대화.
 * 오늘의 학습은 한 곳(강조 카드)에서만 말한다. 시간은 강조 카드, 문제 수는 통계 카드, 연속 학습은 통계 카드에 한 번씩.
 */
export function Home() {
  const profile = useProfile();
  const insets = useSafeAreaInsets();
  const tabBarSpace = useTabBarSpace();
  const daily = useDaily();
  const streak = useStreak();
  const data = daily.data;
  const empty = data !== undefined && data.total === 0 && !data.started;
  // 오늘의 학습 카드와 매일 학습 알림 배너가 같은 시작 요청을 쓴다(준비 중·실패 표시를 함께 본다).
  const start = useStartDaily();

  /** 시작했으면 이어서 풀이로, 시작 전이면 시작해서 풀이로 간다. */
  function openDaily() {
    if (data?.practiceId) {
      openPractice(data.practiceId);
      return;
    }
    start.mutate(undefined, {
      onSuccess: (started) => {
        if (started.practiceId) openPractice(started.practiceId);
      },
    });
  }

  return (
    <ScrollView
      style={styles.screen}
      contentContainerStyle={[styles.content, { paddingTop: insets.top + spacing.lg, paddingBottom: tabBarSpace }]}>
      <HomeHeader />

      <SessionReadyBanner />

      <View style={styles.titleRow}>
        <View style={styles.title}>
          <ThemedText variant="caption" tone="inkMuted">
            {/* 서버의 오늘(시간 이동 데모에서도 맞는 날짜). 불러오기 전에는 기기 날짜. */}
            {today.format(data ? new Date(`${data.date}T00:00:00`) : new Date())} · {profile.name}님
          </ThemedText>
          <ThemedText variant="display">
            {data?.completed ? '오늘 학습을\n마쳤어요' : empty ? '오늘은\n쉬어도 돼요' : '오늘 학습할\n지식이 있어요'}
          </ThemedText>
        </View>
      </View>

      <TodayStudy
        daily={daily.data}
        isPending={daily.isPending}
        isError={daily.isError}
        refetch={() => daily.refetch()}
        start={start}
        onOpen={openDaily}
      />

      <View style={styles.stats}>
        <StatCard
          variant="lavender"
          icon="zap"
          value={streak.data ? String(streak.data.current) : '-'}
          unit="일"
          label={streak.data ? streakLabel(streak.data) : '연속 학습'}
        />
        <StatCard
          variant="surface"
          icon="check-square"
          value={data ? String(data.total) : '-'}
          unit="문제"
          label={data ? `복습 ${data.reviewCount} · 새 항목 ${data.newCount}` : '오늘 문제'}
        />
      </View>

      <MemorySummary />

      <RecentConversations serverToday={data?.date} />
    </ScrollView>
  );
}

/**
 * 오늘의 학습 카드(스펙 §6.4.4). 시작 전이면 시작해서 풀이로, 시작했으면 이어서 풀이로 간다.
 * 할 것이 없거나 다 끝냈으면 누를 수 없는 안내 카드로 바뀐다.
 */
function TodayStudy({
  daily,
  isPending,
  isError,
  refetch,
  start,
  onOpen,
}: {
  daily: Daily | undefined;
  isPending: boolean;
  isError: boolean;
  refetch: () => void;
  start: ReturnType<typeof useStartDaily>;
  onOpen: () => void;
}) {
  const nothingToSolve = start.data !== undefined && start.data.practiceId === null;

  if (isPending) {
    return <Skeleton shape="block" height={components.cardHero.height} />;
  }
  if (isError || !daily) {
    return (
      <Card style={styles.todayCard}>
        <ThemedText tone="dangerInk">오늘의 학습을 불러오지 못했어요.</ThemedText>
        <Button variant="secondary" title="다시 시도" onPress={refetch} />
      </Card>
    );
  }
  if (daily.completed) {
    return (
      <Card variant="lavender" style={styles.todayCard}>
        <ThemedText variant="title">오늘 학습 완료</ThemedText>
        <ThemedText variant="subhead" tone="inkSecondary">
          {daily.total}문제를 풀었어요. 다음 복습은 기억이 떨어질 때쯤 다시 알려드릴게요.
        </ThemedText>
      </Card>
    );
  }
  if (nothingToSolve) {
    return (
      <Card style={styles.todayCard}>
        <ThemedText variant="title">오늘 낼 문제를 준비하지 못했어요</ThemedText>
        <ThemedText variant="subhead" tone="inkSecondary">
          확인을 통과한 문제가 없어 오늘 학습을 시작하지 못했어요. 기억 상태는 바뀌지 않았어요.
        </ThemedText>
        <Button variant="secondary" title="다시 시도" loading={start.isPending} onPress={() => start.mutate()} />
      </Card>
    );
  }
  if (daily.total === 0 && !daily.started) {
    return (
      <Card style={styles.todayCard}>
        <ThemedText variant="title">오늘 복습할 항목이 없어요</ThemedText>
        <ThemedText variant="subhead" tone="inkSecondary">
          기억이 아직 충분해요. 새로 배운 대화를 추가하면 내일부터 함께 복습해요.
        </ThemedText>
        <Button variant="secondary" title="대화 추가하기" onPress={() => router.navigate('/add')} />
      </Card>
    );
  }

  return (
    <View style={styles.today}>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={daily.started ? '오늘의 학습 이어서 하기' : '오늘의 학습 시작하기'}
        accessibilityState={{ busy: start.isPending }}
        disabled={start.isPending}
        onPress={onOpen}>
        {({ pressed }) => (
          <Card variant="hero" pressed={pressed} style={styles.hero}>
            <View style={styles.heroText}>
              <ThemedText variant="title" tone="onPrimary">
                {daily.started ? '오늘의 학습\n이어서 하기' : '오늘의 학습\n시작하기'}
              </ThemedText>
              <ThemedText variant="subhead" tone="onPrimary">
                {start.isPending
                  ? '오늘 풀 문제를 준비하고 있어요…'
                  : `약 ${minutesOf(daily)}분이면 끝나요`}
              </ThemedText>
            </View>
            <View style={styles.heroArrow}>
              {start.isPending ? (
                <ActivityIndicator color={colors.primary} />
              ) : (
                <Icon name="arrow-up-right" size="lg" color={colors.primary} />
              )}
            </View>
          </Card>
        )}
      </Pressable>
      {start.isError && <Notice tone="danger">오늘의 학습을 시작하지 못했어요. 다시 시도해 주세요.</Notice>}
    </View>
  );
}

/**
 * 레퍼런스 홈 헤더: 왼쪽 원형 메뉴 버튼 + 서비스명, 오른쪽 원형 알림 버튼 + 프로필.
 * 메뉴 화면이 없어 메뉴는 받은 학습 대화 목록으로 보낸다. 알림은 알림 목록으로, 프로필은 내 정보로 간다.
 */
function HomeHeader() {
  const profile = useProfile();
  const { data } = useNotifications();
  const unread = data?.items.some((n) => !n.read);

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
        onPress={() => router.push('/notifications')}
        style={({ pressed }) => [styles.circleButton, pressed && styles.circlePressed]}>
        <Icon name="bell" />
        {unread && <View style={styles.unreadDot} />}
      </Pressable>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={`내 정보: ${profile.name}`}
        onPress={() => router.push('/me')}
        style={({ pressed }) => [styles.avatar, pressed && styles.avatarPressed]}>
        <ThemedText variant="headline" tone="primaryInk">
          {profile.initial}
        </ThemedText>
      </Pressable>
    </View>
  );
}

/**
 * 검수를 마친 새 세션의 "학습 내용 도착" 알림. 읽지 않은 것 중 최신 하나만 보여준다.
 * 매일 학습 알림은 바로 아래 오늘의 학습 카드가 같은 일을 하므로 홈에서는 띄우지 않는다(알림 목록에는 남는다).
 */
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
        openNotification(latest);
      }}
      style={({ pressed }) => [styles.banner, pressed && styles.rowPressed]}>
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

/** 기억 게이지 요약: 학습한 세션들의 평균과 가장 약한 세션. 색은 목표 유지율 기준이다. */
function MemorySummary() {
  const overview = useMemoryOverview();
  const { data, isPending, isError } = overview.sessions;
  const { weakest } = overview;

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
        {isPending && (
          <>
            <Skeleton width="50%" />
            <Skeleton height={components.gaugeLarge.height} />
            <Skeleton width="70%" />
          </>
        )}
        {isError && <ThemedText tone="dangerInk">기억 상태를 불러오지 못했어요.</ThemedText>}
        {data?.length === 0 && <ThemedText tone="inkMuted">대화를 추가하면 얼마나 기억하는지 보여드려요.</ThemedText>}
        {data && data.length > 0 && (
          <>
            <Gauge size="lg" value={overview.average} target={overview.target} />
            {weakest && (
              <View style={styles.weakest}>
                <Chip label="가장 약함" />
                <ThemedText variant="subhead" tone="inkSecondary" numberOfLines={1} style={styles.weakestText}>
                  {weakest.session.topicHint ?? '주제 없음'} {percent(weakest.value ?? 0)}%
                </ThemedText>
              </View>
            )}
            <ThemedText variant="caption" tone="inkMuted">
              {overview.average === null
                ? '첫 학습을 하면 얼마나 기억하는지 보여드려요.'
                : '시간이 지나면 떨어지고, 복습하면 다시 올라가요.'}
            </ThemedText>
          </>
        )}
      </Card>
    </View>
  );
}

/** 최근 받은 대화 3개. 한 카드 안에 구분선으로 묶고, 들어온 길(커넥터·링크·붙여넣기)을 아이콘으로 보여준다. */
function RecentConversations({ serverToday }: { serverToday: string | undefined }) {
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

      {isPending && <SkeletonList />}
      {isError && <ThemedText tone="dangerInk">대화를 불러오지 못했어요.</ThemedText>}
      {conversations?.length === 0 && (
        <ThemedText tone="inkMuted">아직 받은 대화가 없어요. Claude에서 “복습에 넣어줘”라고 요청해 보세요.</ThemedText>
      )}
      {recent.length > 0 && (
        <Card style={styles.list}>
          {recent.map((c, i) => (
            <Pressable
              key={c.id}
              accessibilityRole="link"
              onPress={() => router.push({ pathname: '/conversations/[id]', params: { id: c.id } })}
              style={({ pressed }) => [styles.listRow, i > 0 && styles.divided, pressed && styles.rowPressed]}>
              <View style={styles.rowIcon}>
                <Icon name={inputPathIcon[c.inputPath]} size="md" color={colors.primaryInk} />
              </View>
              <View style={styles.rowText}>
                <ThemedText variant="headline" numberOfLines={1}>
                  {c.topicHint ?? '주제 없음'}
                </ThemedText>
                <ThemedText variant="caption" tone="inkMuted">
                  {formatRelativeDay(c.receivedAt, serverToday)} · 주제 {c.reviewUnitCount}개
                </ThemedText>
              </View>
              {c.warningCount > 0 && <View accessibilityLabel={`경고 ${c.warningCount}`} style={styles.warningDot} />}
              <Icon name="chevron-right" size="md" color={colors.inkMuted} />
            </Pressable>
          ))}
        </Card>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.canvas },
  today: { gap: spacing.sm },
  todayCard: { gap: spacing.md },
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
  avatarPressed: { backgroundColor: colors.lavender },
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
  banner: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
    backgroundColor: colors.surface,
    borderRadius: components.card.rounded,
    borderCurve: 'continuous',
    padding: spacing.lg,
    borderWidth: components.answerOptionOutline.width,
    borderColor: colors.primaryTint,
  },
  rowPressed: { backgroundColor: components.cardPressed.backgroundColor },
  list: { paddingVertical: spacing.xs },
  listRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.md, paddingVertical: spacing.md },
  divided: { borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.outline },
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
