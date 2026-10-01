import { router } from 'expo-router';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, View } from 'react-native';

import { useMemoryOverview } from '@/api/memory';
import { useMemoryModel } from '@/api/memory-model';
import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { Chip } from '@/components/chip';
import { Gauge, percent } from '@/components/gauge';
import { Icon } from '@/components/icon';
import { useTabBarSpace } from '@/components/tab-bar';
import { ThemedText } from '@/components/themed-text';
import { formatDateTime, sessionStatusLabel } from '@/labels';
import { colors, spacing } from '@/theme';

/** 학습을 시작한 세션은 세션 상세(게이지)로, 그 전 세션은 세션 확인으로 보낸다. */
function open(session: { id: number; status: string }) {
  const id = String(session.id);
  if (session.status === 'IN_PROGRESS') router.push({ pathname: '/sessions/[id]/memory', params: { id } });
  else router.push({ pathname: '/sessions/[id]', params: { id } });
}

/** 기억 탭: 학습 세션 목록과 세션별 기억 게이지 (스펙 §6.4.3). 색은 세션의 목표 유지율 기준이다. */
export function Memory() {
  const overview = useMemoryOverview();
  const { data, isPending, isError } = overview.sessions;
  const tabBarSpace = useTabBarSpace();

  if (isPending) {
    return (
      <View style={styles.center}>
        <ActivityIndicator color={colors.primary} />
      </View>
    );
  }
  if (isError) {
    return (
      <View style={styles.center}>
        <ThemedText tone="dangerInk">학습 세션을 불러오지 못했어요.</ThemedText>
        <Button variant="secondary" title="다시 시도" onPress={overview.refetch} />
      </View>
    );
  }
  if (data.length === 0) {
    return (
      <View style={styles.center}>
        <ThemedText variant="headline">아직 기억할 내용이 없어요</ThemedText>
        <ThemedText variant="subhead" tone="inkMuted" style={styles.centerText}>
          AI와 나눈 대화를 추가하면 여기서 얼마나 기억하고 있는지 볼 수 있어요.
        </ThemedText>
        <Button title="대화 추가하기" onPress={() => router.navigate('/add')} />
      </View>
    );
  }

  return (
    <ScrollView
      contentInsetAdjustmentBehavior="automatic"
      contentContainerStyle={[styles.content, { paddingBottom: tabBarSpace }]}>
      <Card style={styles.summary}>
        <ThemedText variant="headline">전체 기억</ThemedText>
        <Gauge size="lg" value={overview.average} target={overview.target} />
        {overview.isError && (
          <ThemedText variant="caption" tone="dangerInk">
            일부 세션의 기억 상태를 불러오지 못했어요.
          </ThemedText>
        )}
        <ThemedText variant="caption" tone="inkSecondary">
          세션 {data.length}개 · 기억 항목 {data.reduce((sum, s) => sum + s.itemCount, 0)}개
        </ThemedText>
      </Card>

      <MemoryModelLink />

      <ThemedText variant="headline">학습 세션</ThemedText>
      {data.map((session) => {
        const memory = overview.bySession.get(session.id);
        return (
          <Pressable
            key={session.id}
            accessibilityRole="link"
            accessibilityLabel={`${session.topicHint ?? '주제 없음'}, ${sessionStatusLabel[session.status]}`}
            onPress={() => open(session)}>
            {({ pressed }) => (
              <Card pressed={pressed} style={styles.card}>
                <View style={styles.row}>
                  <ThemedText variant="headline" style={styles.title}>
                    {session.topicHint ?? '주제 없음'}
                  </ThemedText>
                  <Chip
                    variant={session.status === 'AWAITING_CONFIRMATION' ? 'status' : 'soft'}
                    label={sessionStatusLabel[session.status]}
                  />
                </View>
                <ThemedText variant="caption" tone="inkMuted">
                  {formatDateTime(session.createdAt)} · 복습 단위 {session.unitCount}개 · 기억 항목 {session.itemCount}개
                </ThemedText>
                <Gauge value={memory?.value ?? null} target={memory?.target} />
                {memory?.weakest && (
                  <ThemedText variant="caption" tone="inkSecondary" numberOfLines={1}>
                    가장 약한 항목 {percent(memory.weakest.value)}% · {memory.weakest.content}
                  </ThemedText>
                )}
              </Card>
            )}
          </Pressable>
        );
      })}
    </ScrollView>
  );
}

/** 내 기억 패턴 화면으로 가는 행. 지금 쓰는 기억 모델과 개인화 진행도를 한 줄로 보여준다. */
function MemoryModelLink() {
  const { data } = useMemoryModel();
  const status = !data
    ? '망각 곡선과 기억 유지 기간'
    : data.status === 'PERSONALIZED'
      ? `개인 모델 v${data.parametersVersion} 적용 중`
      : `기본 모델 · 복습 기록 ${data.progress.gradedReviews.toLocaleString()} / ${data.progress.requiredReviews.toLocaleString()}`;
  return (
    <Pressable
      accessibilityRole="link"
      accessibilityLabel={`내 기억 패턴, ${status}`}
      onPress={() => router.push('/memory-model')}>
      {({ pressed }) => (
        <Card pressed={pressed} style={styles.link}>
          <View style={styles.linkText}>
            <ThemedText variant="headline">내 기억 패턴</ThemedText>
            <ThemedText variant="caption" tone="inkSecondary">
              {status}
            </ThemedText>
          </View>
          <Icon name="chevron-right" color={colors.inkMuted} />
        </Card>
      )}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  center: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: spacing.md,
    padding: spacing.xl,
  },
  centerText: { textAlign: 'center' },
  content: { padding: spacing.xl, gap: spacing.md },
  summary: { gap: spacing.md, marginBottom: spacing.md },
  card: { gap: spacing.sm },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: spacing.sm,
  },
  title: { flexShrink: 1 },
  link: { flexDirection: 'row', alignItems: 'center', gap: spacing.md, marginBottom: spacing.md },
  linkText: { flex: 1, gap: spacing.xs },
});
