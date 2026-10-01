import { router } from 'expo-router';
import { ActivityIndicator, ScrollView, StyleSheet, View } from 'react-native';

import { useLearningSessions } from '@/api/learning-sessions';
import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { Chip } from '@/components/chip';
import { Gauge } from '@/components/gauge';
import { useTabBarSpace } from '@/components/tab-bar';
import { ThemedText } from '@/components/themed-text';
import { formatDateTime, sessionStatusLabel } from '@/labels';
import { colors, spacing } from '@/theme';

import { sampleAverage, sampleRetrievability } from './sample-gauges';

/** 기억 탭: 학습 세션 목록과 세션별 기억 게이지 (스펙 §6.4.3). 게이지 값은 API 전까지 예시다. */
export function Memory() {
  const { data, isPending, isError, refetch } = useLearningSessions();
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
        <Button variant="secondary" title="다시 시도" onPress={() => refetch()} />
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
      <Card variant="lavender" style={styles.summary}>
        <View style={styles.row}>
          <ThemedText variant="headline">전체 기억</ThemedText>
          <Chip label="예시 값" />
        </View>
        <Gauge size="lg" value={sampleAverage(data)} />
        <ThemedText variant="caption" tone="inkSecondary">
          세션 {data.length}개 · 기억 항목 {data.reduce((sum, s) => sum + s.itemCount, 0)}개
        </ThemedText>
      </Card>

      <ThemedText variant="headline">학습 세션</ThemedText>
      {data.map((session) => (
        <Card key={session.id} style={styles.card}>
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
          <Gauge value={sampleRetrievability(session)} />
        </Card>
      ))}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: spacing.md, padding: spacing.xl },
  centerText: { textAlign: 'center' },
  content: { padding: spacing.xl, gap: spacing.md },
  summary: { gap: spacing.md, marginBottom: spacing.md },
  card: { gap: spacing.sm },
  row: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing.sm },
  title: { flexShrink: 1 },
});
