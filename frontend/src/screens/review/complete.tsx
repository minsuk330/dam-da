import { router } from 'expo-router';
import { ActivityIndicator, ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import type { Schemas } from '@/api/client';
import { useStreak } from '@/api/daily';
import { useFirstStudySummary, useTargetRetention } from '@/api/memory';
import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { Chip } from '@/components/chip';
import { Gauge, percent } from '@/components/gauge';
import { Icon } from '@/components/icon';
import { ThemedText } from '@/components/themed-text';
import { formatDate } from '@/labels';
import { colors, components, spacing } from '@/theme';

/**
 * 풀이 경로(스펙 §7 6단계). 서버 피드백의 경로를 따르고, 설명 뒤 확인 문제 결과로 최종 경로를 정한다.
 * 지식 상태 표시와 피드백에만 쓰고, FSRS 등급은 첫 무도움 시도로 서버가 정한다. `held`는 판정 보류다.
 */
export type Path = 'independent' | 'afterHint' | 'afterExplanation' | 'repeatedWrong' | 'held';

const COUNTED: Exclude<Path, 'held'>[] = ['independent', 'afterHint', 'afterExplanation', 'repeatedWrong'];

export const pathLabel: Record<Path, string> = {
  independent: '혼자 맞힘',
  afterHint: '힌트 후 맞힘',
  afterExplanation: '설명 후 맞힘',
  repeatedWrong: '다시 볼 항목',
  held: '판정 보류',
};

/** `explained`는 이 문제에서 개념 설명을 봤는지다. 다시 물은 문제를 맞혔을 때 경로를 정하는 데 쓴다. */
export type ItemResult = { questionId: number; stem: string; path: Path; explained: boolean };

/**
 * 학습 완료: 풀이 경로 집계와, 세션이 있으면 첫 학습 요약(확인한 항목·도움이 필요했던 항목·다음 복습 일정·단위 게이지).
 * 요약은 서버가 FSRS로 계산한 값이다.
 */
export function ReviewComplete({ results, sessionId }: { results: ItemResult[]; sessionId: number | null }) {
  const insets = useSafeAreaInsets();

  return (
    <ScrollView contentContainerStyle={[styles.content, { paddingBottom: insets.bottom + spacing['3xl'] }]}>
      <View style={styles.hero}>
        <View style={styles.doneIcon}>
          <Icon name="check" size="xl" color={colors.onPrimary} />
        </View>
        <ThemedText variant="display" style={styles.center}>
          학습을 마쳤어요
        </ThemedText>
        {results.length > 0 && (
          <ThemedText variant="subhead" tone="inkSecondary" style={styles.center}>
            {results.length}문제를 풀었어요
          </ThemedText>
        )}
      </View>

      {/* 이미 끝난 풀이를 다시 열면 이번 풀이 기록이 없으므로 경로 집계는 숨기고 요약만 보여준다. */}
      {results.length > 0 && (
        <View style={styles.paths}>
          {COUNTED.map((path) => (
            <Card key={path} variant={path === 'independent' ? 'lavender' : 'surface'} style={styles.pathCard}>
              <ThemedText variant="stat">{results.filter((r) => r.path === path).length}</ThemedText>
              <ThemedText variant="caption" tone="inkSecondary">
                {pathLabel[path]}
              </ThemedText>
            </Card>
          ))}
        </View>
      )}

      {sessionId !== null ? (
        <SessionSummary sessionId={sessionId} />
      ) : (
        <>
          <StreakCard />
          <HelpedQuestions results={results} />
        </>
      )}

      <Button title="홈으로" onPress={() => router.navigate('/')} />
    </ScrollView>
  );
}

type SummaryItem = Schemas['SummaryItem'];

function SessionSummary({ sessionId }: { sessionId: number }) {
  const { data, isPending, isError, refetch } = useFirstStudySummary(sessionId);
  const target = useTargetRetention(sessionId);

  if (isPending) return <ActivityIndicator color={colors.primary} />;
  if (isError) {
    return (
      <View style={styles.section}>
        <ThemedText tone="dangerInk">학습 요약을 불러오지 못했어요.</ThemedText>
        <Button variant="secondary" title="다시 시도" onPress={() => refetch()} />
      </View>
    );
  }

  return (
    <>
      {data.nextReviewAt && (
        <Card variant="lavender" style={styles.next}>
          <ThemedText variant="caption" tone="inkSecondary">
            다음 복습
          </ThemedText>
          <ThemedText variant="title">{formatDate(data.nextReviewAt)}</ThemedText>
        </Card>
      )}

      <ItemList title="도움이 필요했던 항목" items={data.needsHelp} target={target} />
      <ItemList title="확인한 항목" items={data.confirmed} target={target} />
      {data.notChecked.length > 0 && (
        <ThemedText variant="caption" tone="inkMuted">
          아직 풀어 보지 않은 항목 {data.notChecked.length}개는 다음 학습에서 확인해요.
        </ThemedText>
      )}

      {data.units.length > 0 && (
        <View style={styles.section}>
          <ThemedText variant="title">지금 내 기억</ThemedText>
          <Card style={styles.list}>
            {data.units.map((unit) => (
              <Gauge key={unit.unitId} label={unit.title} value={unit.gauge.average} target={target} />
            ))}
          </Card>
          <Button
            variant="secondary"
            title="기억 상태 자세히 보기"
            onPress={() => router.push({ pathname: '/sessions/[id]/memory', params: { id: String(sessionId) } })}
          />
        </View>
      )}
    </>
  );
}

function ItemList({ title, items, target }: { title: string; items: SummaryItem[]; target: number }) {
  if (items.length === 0) return null;
  return (
    <View style={styles.section}>
      <ThemedText variant="title">{title}</ThemedText>
      <Card style={styles.list}>
        {items.map((item, i) => (
          <View key={item.memoryItemId} style={[styles.item, i > 0 && styles.divided]}>
            <ThemedText variant="caption" tone="inkMuted">
              {item.unitTitle}
            </ThemedText>
            <ThemedText variant="subhead">{item.content}</ThemedText>
            <View style={styles.itemFooter}>
              {item.gauge.retrievability !== null && (
                <Chip
                  variant={item.gauge.retrievability >= target ? 'soft' : 'warning'}
                  label={`지금 ${percent(item.gauge.retrievability)}%`}
                />
              )}
              {item.nextReviewAt && (
                <ThemedText variant="caption" tone="primaryInk">
                  다음 복습 {formatDate(item.nextReviewAt)}
                </ThemedText>
              )}
            </View>
          </View>
        ))}
      </Card>
    </View>
  );
}

/** 매일 학습을 끝내면 연속 학습 일수가 하루 는다(스펙 §7.7). 불러오지 못하면 보여주지 않는다. */
function StreakCard() {
  const { data } = useStreak();
  if (!data || data.current === 0) return null;
  return (
    <Card variant="lavender" style={styles.next}>
      <ThemedText variant="caption" tone="inkSecondary">
        연속 학습
      </ThemedText>
      <ThemedText variant="title">{data.current}일째 이어가고 있어요</ThemedText>
      {data.best > data.current && (
        <ThemedText variant="caption" tone="inkSecondary">
          최고 기록 {data.best}일
        </ThemedText>
      )}
    </Card>
  );
}

/** 매일 학습은 세션 요약이 없으므로 문제 단위로 도움이 필요했던 문제를 보여준다. */
function HelpedQuestions({ results }: { results: ItemResult[] }) {
  const helped = results.filter((r) => r.path !== 'independent');
  if (helped.length === 0) return null;
  return (
    <View style={styles.section}>
      <ThemedText variant="title">도움이 필요했던 문제</ThemedText>
      <Card style={styles.list}>
        {helped.map(({ questionId, stem, path }, i) => (
          <View key={questionId} style={[styles.item, i > 0 && styles.divided]}>
            <View style={styles.itemFooter}>
              <Chip label={pathLabel[path]} />
            </View>
            <ThemedText variant="subhead">{stem}</ThemedText>
          </View>
        ))}
      </Card>
    </View>
  );
}

const styles = StyleSheet.create({
  content: { padding: spacing.xl, gap: spacing['2xl'] },
  hero: { alignItems: 'center', gap: spacing.md, paddingTop: spacing.xl },
  center: { textAlign: 'center' },
  doneIcon: {
    width: components.doneMark.size,
    height: components.doneMark.size,
    borderRadius: components.doneMark.rounded,
    backgroundColor: components.doneMark.backgroundColor,
    alignItems: 'center',
    justifyContent: 'center',
  },
  paths: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.md },
  pathCard: { flexBasis: '47%', flexGrow: 1, gap: spacing.xs },
  next: { gap: spacing.xs },
  section: { gap: spacing.md },
  list: { gap: spacing.lg },
  item: { gap: spacing.sm },
  divided: { borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.outline, paddingTop: spacing.lg },
  itemFooter: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm },
});
