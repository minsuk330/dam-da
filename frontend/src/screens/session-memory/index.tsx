import { router, Stack } from 'expo-router';
import { ActivityIndicator, ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { useLearningSession } from '@/api/learning-sessions';
import { gaugeOf, useFirstStudySummary, useSessionGauge, useTargetRetention, type UnitView } from '@/api/memory';
import { useStartFirstStudy } from '@/api/practice';
import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { Chip } from '@/components/chip';
import { Gauge, percent } from '@/components/gauge';
import { Notice } from '@/components/notice';
import { ThemedText } from '@/components/themed-text';
import { formatDate, itemKindLabel } from '@/labels';
import { colors, spacing } from '@/theme';

/**
 * 세션 상세 (스펙 §6.4.3): 세션 → 복습 단위(평균 R, 가장 약한 항목) → 기억 항목(R, 다음 복습일, 아직 확인 전).
 * 색은 이 세션에서 고른 기억 강도의 목표 유지율 기준이다.
 */
export function SessionMemory({ id }: { id: number }) {
  const insets = useSafeAreaInsets();
  const session = useLearningSession(id);
  const gauge = useSessionGauge(id);
  const summary = useFirstStudySummary(id);
  const target = useTargetRetention(id);
  const start = useStartFirstStudy(id);

  if (gauge.isPending || session.isPending) {
    return (
      <View style={styles.center}>
        <ActivityIndicator color={colors.primary} />
      </View>
    );
  }
  if (gauge.isError || session.isError) {
    return (
      <View style={styles.center}>
        <ThemedText tone="dangerInk">기억 상태를 불러오지 못했어요.</ThemedText>
        <Button
          variant="secondary"
          title="다시 시도"
          onPress={() => {
            session.refetch();
            gauge.refetch();
          }}
        />
      </View>
    );
  }

  const units = gauge.data.units;
  const { value } = gaugeOf(units);
  const nextReview = new Map(
    // 첫 학습 뒤 매일 학습에서 처음 푼 항목은 아직 확인 전 목록에 있으므로 세 목록을 모두 본다.
    [...(summary.data?.confirmed ?? []), ...(summary.data?.needsHelp ?? []), ...(summary.data?.notChecked ?? [])].map(
      (i) => [i.memoryItemId, i.nextReviewAt],
    ),
  );
  const unfinished = summary.data && !summary.data.completed;

  function resume() {
    start.mutate(undefined, {
      onSuccess: (practice) =>
        router.push({ pathname: '/review', params: { practiceId: String(practice.practiceId), sessionId: String(id) } }),
    });
  }

  return (
    <ScrollView contentContainerStyle={[styles.content, { paddingBottom: insets.bottom + spacing['3xl'] }]}>
      <Stack.Screen options={{ title: session.data.topicHint ?? '기억 상태' }} />

      <Card style={styles.card}>
        <ThemedText variant="headline">세션 전체</ThemedText>
        <Gauge size="lg" value={value} target={target} />
        <ThemedText variant="caption" tone="inkSecondary">
          목표 유지율 {percent(target)}%
          {summary.data?.nextReviewAt ? ` · 다음 복습 ${formatDate(summary.data.nextReviewAt)}` : ''}
        </ThemedText>
      </Card>

      {unfinished && (
        <>
          {start.isError && <Notice tone="danger">첫 학습을 열지 못했어요. 다시 시도해 주세요.</Notice>}
          <Button title="첫 학습 이어서 하기" loading={start.isPending} onPress={resume} />
        </>
      )}

      {units.length === 0 ? (
        <ThemedText tone="inkMuted" style={styles.centerText}>
          복습할 단위가 없어요.
        </ThemedText>
      ) : (
        units.map((unit) => (
          <UnitCard key={unit.unitId} unit={unit} target={target} nextReview={nextReview} />
        ))
      )}

      <Button
        variant="secondary"
        title="원본 대화 보기"
        onPress={() => router.push({ pathname: '/conversations/[id]', params: { id: session.data.conversationId } })}
      />
    </ScrollView>
  );
}

function UnitCard({
  unit,
  target,
  nextReview,
}: {
  unit: UnitView;
  target: number;
  nextReview: Map<number, string | null>;
}) {
  const { average, checkedItems, totalItems, weakestItemId, weakestPercent } = unit.gauge;
  const weakest = unit.items.find((i) => i.memoryItemId === weakestItemId);

  return (
    <View style={styles.section}>
      <ThemedText variant="title">{unit.title}</ThemedText>
      <Card style={styles.card}>
        <Gauge size="lg" label="단위 평균" value={average} target={target} />
        <ThemedText variant="caption" tone="inkSecondary">
          확인한 항목 {checkedItems}/{totalItems}
        </ThemedText>
        {weakest && weakestPercent !== null && (
          <View style={styles.weakest}>
            <Chip label="가장 약함" />
            <ThemedText variant="subhead" tone="inkSecondary" numberOfLines={2} style={styles.weakestText}>
              {weakestPercent}% · {weakest.content}
            </ThemedText>
          </View>
        )}
      </Card>

      <Card style={styles.items}>
        {unit.items.map((item, i) => {
          const next = nextReview.get(item.memoryItemId);
          return (
            <View key={item.memoryItemId} style={[styles.item, i > 0 && styles.divided]}>
              <View style={styles.itemHeader}>
                <Chip label={itemKindLabel[item.kind]} />
              </View>
              <ThemedText variant="subhead">{item.content}</ThemedText>
              <Gauge value={item.gauge.retrievability} target={target} />
              <ThemedText variant="caption" tone="inkMuted">
                {item.gauge.checked ? (next ? `다음 복습 ${formatDate(next)}` : '다음 복습일 계산 전') : '아직 풀어 보지 않았어요'}
              </ThemedText>
            </View>
          );
        })}
      </Card>
    </View>
  );
}

const styles = StyleSheet.create({
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: spacing.md, padding: spacing.xl },
  centerText: { textAlign: 'center' },
  content: { padding: spacing.xl, gap: spacing.xl },
  card: { gap: spacing.md },
  section: { gap: spacing.md },
  weakest: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm },
  weakestText: { flex: 1 },
  items: { gap: spacing.lg },
  item: { gap: spacing.sm },
  itemHeader: { flexDirection: 'row' },
  divided: { borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.outline, paddingTop: spacing.lg },
});
