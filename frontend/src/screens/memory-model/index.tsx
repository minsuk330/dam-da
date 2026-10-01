import { ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { useMemoryModel, type CurvePoint, type MemoryModel as Model } from '@/api/memory-model';
import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { Chip } from '@/components/chip';
import { percent } from '@/components/gauge';
import { SkeletonScreen } from '@/components/skeleton';
import { StatCard } from '@/components/stat-card';
import { ThemedText } from '@/components/themed-text';
import { components, spacing } from '@/theme';

import { ForgettingCurve } from './forgetting-curve';

/** 일수 표시: 10일 미만은 소수 한 자리, 그 이상은 정수. */
function days(value: number) {
  return value < 10 ? value.toFixed(1) : String(Math.round(value));
}

const at = (curve: CurvePoint[], day: number) => curve.find((p) => p.day === day)?.retrievability ?? null;

/**
 * 내 기억 패턴 (스펙 §6.4.9, #72). 사용자가 쓰는 FSRS 기억 모델의 망각 곡선·기억 유지 기간과,
 * 복습 기록이 쌓여 개인 모델로 바뀌는 진행도를 보여준다. 값은 모두 서버가 FSRS로 계산한다.
 */
export function MemoryModel() {
  const insets = useSafeAreaInsets();
  const model = useMemoryModel();

  if (model.isPending) {
    return <SkeletonScreen blocks={[components.cardStat.height, components.chart.height]} />;
  }
  if (model.isError) {
    return (
      <View style={styles.center}>
        <ThemedText tone="dangerInk">기억 패턴을 불러오지 못했어요.</ThemedText>
        <Button variant="secondary" title="다시 시도" onPress={() => model.refetch()} />
      </View>
    );
  }

  const data = model.data;
  return (
    <ScrollView contentContainerStyle={[styles.content, { paddingBottom: insets.bottom + spacing['3xl'] }]}>
      <StatusCard model={data} />

      <Card style={styles.card}>
        <ThemedText variant="headline">오늘 맞힌 지식, 얼마나 기억할까요</ThemedText>
        <ForgettingCurve curve={data.curve} defaultCurve={data.defaultCurve} />
        <CurveSummary curve={data.curve} defaultCurve={data.defaultCurve} />
      </Card>

      <View style={styles.stats}>
        <StatCard
          icon="zap"
          variant="lavender"
          value={days(data.firstRecallDays)}
          unit="일"
          label="처음 맞힌 지식이 90% 아래로 떨어지기까지"
        />
        <StatCard
          icon="clock"
          variant="surface"
          value={data.typicalStabilityDays === null ? '-' : days(data.typicalStabilityDays)}
          unit={data.typicalStabilityDays === null ? '' : '일'}
          label={data.reviewedItems === 0 ? '복습한 지식이 아직 없어요' : `복습한 지식 ${data.reviewedItems}개의 기억 유지 기간`}
        />
      </View>

      <ThemedText variant="caption" tone="inkMuted" style={styles.note}>
        기억 유지 기간은 기억할 확률이 90%로 떨어질 때까지의 일수예요. FSRS 기억 모델로 계산했어요.
      </ThemedText>
    </ScrollView>
  );
}

/** 지금 쓰는 기억 모델과 개인화 진행도. */
function StatusCard({ model }: { model: Model }) {
  const { gradedReviews, requiredReviews } = model.progress;
  const personalized = model.status === 'PERSONALIZED';
  const ratio = Math.min(1, gradedReviews / requiredReviews);

  return (
    <Card style={styles.card}>
      <Chip
        variant={personalized ? 'status' : 'soft'}
        label={personalized ? `개인 모델 v${model.parametersVersion} 적용 중` : '기본 모델 사용 중'}
      />
      <ThemedText variant="title">
        {personalized ? '내 기록에 맞춘 기억 모델이에요' : '복습 기록이 쌓이면 나에게 맞춰져요'}
      </ThemedText>
      <ThemedText variant="subhead" tone="inkSecondary">
        {personalized
          ? `내 복습 기록으로 학습하고 검증했더니 기본 모델보다 기억 예측이 ${percentOne(model.predictionImprovement ?? 0)}% 더 정확해서 적용했어요.`
          : gradedReviews === 0
            ? '문제를 풀면 복습 기록이 쌓여요. 충분히 모이면 내가 잊는 속도를 학습해요.'
            : '내가 잊는 속도를 학습하려면 복습 기록이 더 필요해요. 학습한 모델이 기본보다 정확할 때만 바꿔요.'}
      </ThemedText>
      <View style={styles.progress}>
        <View style={styles.progressLabel}>
          <ThemedText variant="caption" tone="inkSecondary">
            복습 기록
          </ThemedText>
          <ThemedText variant="caption" tone="inkSecondary">
            {gradedReviews >= requiredReviews
              ? `${gradedReviews.toLocaleString()}개 · 기준 ${requiredReviews.toLocaleString()}개 넘음`
              : `${gradedReviews.toLocaleString()} / ${requiredReviews.toLocaleString()}`}
          </ThemedText>
        </View>
        <View
          accessible
          accessibilityRole="progressbar"
          accessibilityLabel="개인 기억 모델까지 복습 기록"
          accessibilityValue={{ min: 0, max: requiredReviews, now: Math.min(gradedReviews, requiredReviews) }}
          style={styles.progressTrack}>
          <View style={[styles.progressFill, { width: `${Math.round(ratio * 100)}%` }]} />
        </View>
      </View>
    </Card>
  );
}

/** 차트 값을 글자로: 7일·30일 뒤 기억할 확률(비교 곡선이 있으면 함께). */
function CurveSummary({ curve, defaultCurve }: { curve: CurvePoint[]; defaultCurve: CurvePoint[] | null }) {
  return (
    <View style={styles.summary}>
      {[7, 30].map((day) => {
        const mine = at(curve, day);
        const base = defaultCurve ? at(defaultCurve, day) : null;
        if (mine === null) return null;
        return (
          <ThemedText key={day} variant="subhead" tone="inkSecondary">
            {day}일 뒤 {percent(mine)}%{base !== null ? ` · 기본 모델 ${percent(base)}%` : ''}
          </ThemedText>
        );
      })}
    </View>
  );
}

/** 0.067 → "6.7" */
function percentOne(value: number) {
  return (Math.round(value * 1000) / 10).toFixed(1);
}

const styles = StyleSheet.create({
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: spacing.md, padding: spacing.xl },
  content: { padding: spacing.xl, gap: spacing.md },
  card: { gap: spacing.md },
  progress: { gap: spacing.xs },
  progressLabel: { flexDirection: 'row', justifyContent: 'space-between' },
  progressTrack: {
    height: components.gauge.height,
    borderRadius: components.gauge.rounded,
    backgroundColor: components.gauge.backgroundColor,
    overflow: 'hidden',
  },
  progressFill: { height: '100%', borderRadius: components.gaugeFill.rounded, backgroundColor: components.gaugeFill.backgroundColor },
  summary: { gap: spacing.xs },
  stats: { flexDirection: 'row', gap: spacing.md },
  note: { paddingHorizontal: spacing.xs },
});
