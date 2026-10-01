import { router } from 'expo-router';
import { ActivityIndicator, StyleSheet, View } from 'react-native';

import { useFirstStudy } from '@/api/learning-sessions';
import { useStartFirstStudy } from '@/api/practice';
import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { Notice } from '@/components/notice';
import { ThemedText } from '@/components/themed-text';
import { questionTypeLabel } from '@/labels';
import { colors, spacing } from '@/theme';


/**
 * 문제 준비: 문제를 만들고 Jev 품질 검사를 통과한 것만 내놓는다. 통과·보류 수를 그대로 보여준다(데모 포인트).
 * 품질 검사 전 문제는 보여주지 않는다(규칙: 문제 후보는 품질 검사 통과 전 노출하지 않는다).
 */
export function PrepareStep({ sessionId, onNoPlan }: { sessionId: number; onNoPlan: () => void }) {
  const { data, isPending, isError, refetch } = useFirstStudy(sessionId, true);
  const start = useStartFirstStudy(sessionId);

  if (isPending) {
    return (
      <View style={styles.center}>
        <ActivityIndicator color={colors.primary} />
      </View>
    );
  }
  if (isError) {
    return (
      <View style={styles.step}>
        <Notice tone="danger">문제 준비 상태를 불러오지 못했어요.</Notice>
        <Button variant="secondary" title="다시 시도" onPress={() => refetch()} />
      </View>
    );
  }
  if (data.failed) {
    return (
      <View style={styles.step}>
        <ThemedText variant="title">문제를 만들지 못했어요</ThemedText>
        <Notice tone="danger">{data.failureReason ?? '잠시 후 다시 시도해 주세요.'}</Notice>
        <Button title="학습 목표 다시 고르기" onPress={onNoPlan} />
      </View>
    );
  }
  if (!data.generating && data.planned === 0) {
    return (
      <View style={styles.step}>
        <ThemedText variant="title">아직 학습 목표를 고르지 않았어요</ThemedText>
        <Button title="학습 목표 고르기" onPress={onNoPlan} />
      </View>
    );
  }
  if (data.generating) {
    return (
      <View style={styles.center}>
        <ActivityIndicator size="large" color={colors.primary} />
        <ThemedText variant="title">문제를 만들고 있어요</ThemedText>
        <ThemedText variant="subhead" tone="inkMuted" style={styles.centerText}>
          문제 {data.planned}개를 만들고 품질 검사를 하고 있어요.{'\n'}검사를 통과한 문제만 보여드려요.
        </ThemedText>
      </View>
    );
  }

  const passed = data.questions.length;
  const held = data.held.length;

  return (
    <View style={styles.step}>
      <ThemedText variant="display">첫 학습이{'\n'}준비됐어요</ThemedText>

      <Card variant="lavender" style={styles.quality}>
        <ThemedText variant="headline">품질 검사 결과</ThemedText>
        <View style={styles.stats}>
          <Stat value={data.planned} label="만든 문제" />
          <Stat value={passed} label="통과" />
          <Stat value={held} label="보류" />
        </View>
        <ThemedText variant="caption" tone="inkSecondary">
          {held > 0
            ? `보류된 ${held}개(${data.held.map((h) => questionTypeLabel[h.type]).join(', ')})는 품질 검사를 통과하지 못해 이번 첫 학습에서 빠졌어요. 검사를 통과한 문제만 풀어요.`
            : '모든 문제가 품질 검사를 통과했어요.'}
        </ThemedText>
      </Card>

      <View style={styles.types}>
        {summarizeTypes(data.questions.map((q) => q.type)).map(([type, count]) => (
          <ThemedText key={type} variant="subhead" tone="inkSecondary">
            {questionTypeLabel[type]} {count}개
          </ThemedText>
        ))}
      </View>

      {start.isError && <Notice tone="danger">첫 학습을 시작하지 못했어요. 다시 시도해 주세요.</Notice>}
      <Button
        title={`첫 학습 시작 · ${passed}문제`}
        disabled={passed === 0}
        loading={start.isPending}
        onPress={() =>
          start.mutate(undefined, {
            onSuccess: (practice) =>
              router.push({ pathname: '/review', params: { practiceId: String(practice.practiceId), sessionId: String(sessionId) } }),
          })
        }
      />
      <Button variant="secondary" title="나중에 하기" onPress={() => router.navigate('/memory')} />
    </View>
  );
}

function Stat({ value, label }: { value: number; label: string }) {
  return (
    <View style={styles.stat}>
      <ThemedText variant="stat">{value}</ThemedText>
      <ThemedText variant="caption" tone="inkSecondary">
        {label}
      </ThemedText>
    </View>
  );
}

function summarizeTypes<T extends string>(types: T[]): [T, number][] {
  const counts = new Map<T, number>();
  for (const type of types) counts.set(type, (counts.get(type) ?? 0) + 1);
  return [...counts.entries()];
}

const styles = StyleSheet.create({
  step: { gap: spacing.md },
  center: { alignItems: 'center', gap: spacing.md, paddingVertical: spacing['3xl'] },
  centerText: { textAlign: 'center' },
  quality: { gap: spacing.md },
  stats: { flexDirection: 'row' },
  stat: { flex: 1, alignItems: 'center', gap: spacing.xs },
  types: { flexDirection: 'row', flexWrap: 'wrap', columnGap: spacing.md, justifyContent: 'center' },
});
