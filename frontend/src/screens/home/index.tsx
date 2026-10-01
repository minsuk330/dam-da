import { Link, router } from 'expo-router';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { useConversations } from '@/api/conversations';
import { Card } from '@/components/card';
import { Chip } from '@/components/chip';
import { Icon } from '@/components/icon';
import { useTabBarSpace } from '@/components/tab-bar';
import { ThemedText } from '@/components/themed-text';
import { formatDateTime } from '@/labels';
import { sampleMinutes, sampleQuestions } from '@/screens/review/sample-questions';
import { colors, components, spacing } from '@/theme';

import { StatCard } from './stat-card';

const today = new Intl.DateTimeFormat('ko-KR', { month: 'long', day: 'numeric', weekday: 'long' });

export function Home() {
  const insets = useSafeAreaInsets();
  const tabBarSpace = useTabBarSpace();
  const { data: conversations, isPending, isError } = useConversations();
  const recent = conversations ? [...conversations].reverse().slice(0, 3) : [];

  return (
    <ScrollView
      style={styles.screen}
      contentContainerStyle={[styles.content, { paddingTop: insets.top + spacing.lg, paddingBottom: tabBarSpace }]}>
      <View style={styles.header}>
        <View style={styles.headerText}>
          <ThemedText variant="caption" tone="inkMuted">
            {today.format(new Date())}
          </ThemedText>
          <ThemedText variant="headline">지원님, 안녕하세요</ThemedText>
        </View>
        <View accessibilityLabel="프로필: 지원" style={styles.avatar}>
          <ThemedText variant="headline" tone="primaryInk">
            지
          </ThemedText>
        </View>
      </View>

      <View style={styles.titleRow}>
        <ThemedText variant="display" style={styles.title}>
          오늘 복습할{'\n'}지식이 있어요
        </ThemedText>
        <Chip variant="status" label={`${sampleQuestions.length}문제`} style={styles.titleChip} />
      </View>

      <View style={styles.stats}>
        <StatCard variant="lavender" icon="clock" value={String(sampleMinutes)} unit="분" label="오늘 예상 복습 시간" />
        <StatCard
          variant="surface"
          icon="message-circle"
          value={conversations ? String(conversations.length) : '–'}
          unit="개"
          label="받은 학습 대화"
        />
      </View>

      <Pressable
        accessibilityRole="button"
        accessibilityLabel="오늘의 복습 시작하기"
        onPress={() => router.push('/review')}
        style={({ pressed }) => pressed && styles.pressed}>
        <Card variant="hero" style={styles.hero}>
          <View style={styles.heroText}>
            <ThemedText variant="title" tone="onPrimary">
              오늘의 복습{'\n'}시작하기
            </ThemedText>
            <ThemedText variant="subhead" tone="onPrimary">
              {sampleQuestions.length}문제 · 약 {sampleMinutes}분 · 예시 문제
            </ThemedText>
          </View>
          <View style={styles.heroArrow}>
            <Icon name="arrow-up-right" size={22} color={colors.primary} />
          </View>
        </Card>
      </Pressable>

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
                <Icon name="book-open" size={18} color={colors.primaryInk} />
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
              <Icon name="chevron-right" size={18} color={colors.inkMuted} />
          </Pressable>
        ))}
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.canvas },
  content: { paddingHorizontal: spacing.xl, gap: spacing['2xl'] },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  headerText: { gap: 2 },
  avatar: {
    width: components.iconButton.size,
    height: components.iconButton.size,
    borderRadius: components.iconButton.rounded,
    backgroundColor: colors.primaryTint,
    alignItems: 'center',
    justifyContent: 'center',
  },
  titleRow: { flexDirection: 'row', alignItems: 'flex-start', justifyContent: 'space-between', gap: spacing.md },
  title: { flex: 1 },
  titleChip: { marginTop: spacing.sm },
  stats: { flexDirection: 'row', gap: spacing.md },
  hero: { flexDirection: 'row', alignItems: 'flex-end', justifyContent: 'space-between', minHeight: 156 },
  heroText: { gap: spacing.sm, flex: 1 },
  heroArrow: {
    width: components.iconButton.size,
    height: components.iconButton.size,
    borderRadius: components.iconButton.rounded,
    backgroundColor: colors.surface,
    alignItems: 'center',
    justifyContent: 'center',
  },
  pressed: { opacity: 0.9 },
  section: { gap: spacing.md },
  sectionHeader: { flexDirection: 'row', alignItems: 'baseline', justifyContent: 'space-between' },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
    backgroundColor: colors.surface,
    borderRadius: components.card.rounded,
    borderCurve: 'continuous',
    padding: spacing.lg,
  },
  rowPressed: { backgroundColor: colors.surfaceSoft },
  rowIcon: {
    width: 40,
    height: 40,
    borderRadius: components.iconButton.rounded,
    backgroundColor: colors.primaryTint,
    alignItems: 'center',
    justifyContent: 'center',
  },
  rowText: { flex: 1, gap: 2 },
  warningDot: {
    width: components.warningMark.size,
    height: components.warningMark.size,
    borderRadius: components.warningMark.size,
    backgroundColor: components.warningMark.backgroundColor,
  },
});
