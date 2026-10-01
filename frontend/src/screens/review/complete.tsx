import { router } from 'expo-router';
import { ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { Chip } from '@/components/chip';
import { Gauge } from '@/components/gauge';
import { Icon } from '@/components/icon';
import { ThemedText } from '@/components/themed-text';
import { colors, components, spacing } from '@/theme';

import type { Question } from './sample-questions';

/** 풀이 경로(스펙 §7 6단계). 지식 상태 표시와 피드백에만 쓰고, FSRS 등급은 첫 무도움 시도로 정한다. */
export type Path = 'independent' | 'afterHint' | 'afterExplanation' | 'repeatedWrong';

export const pathLabel: Record<Path, string> = {
  independent: '혼자 맞힘',
  afterHint: '힌트 후 맞힘',
  afterExplanation: '설명 후 맞힘',
  repeatedWrong: '다시 볼 항목',
};

export type ItemResult = { question: Question; path: Path };

// 다음 복습 일정과 게이지는 FSRS가 계산한다(복습 기록 API #19, 게이지 API #21). 그 전까지 경로별 예시 값.
const SAMPLE_NEXT: Record<Path, string> = {
  independent: '4일 뒤',
  afterHint: '2일 뒤',
  afterExplanation: '내일',
  repeatedWrong: '오늘 한 번 더',
};
const SAMPLE_R: Record<Path, number> = { independent: 0.95, afterHint: 0.86, afterExplanation: 0.74, repeatedWrong: 0.52 };

/** 학습 완료: 확인한 항목, 도움이 필요했던 항목, 다음 복습 일정, 복습 단위 게이지. */
export function ReviewComplete({ results }: { results: ItemResult[] }) {
  const insets = useSafeAreaInsets();
  const helped = results.filter((r) => r.path !== 'independent');
  const units = [...new Set(results.map((r) => r.question.unitTitle))].map((title) => {
    const values = results.filter((r) => r.question.unitTitle === title).map((r) => SAMPLE_R[r.path]);
    return { title, value: values.reduce((sum, v) => sum + v, 0) / values.length };
  });

  return (
    <ScrollView contentContainerStyle={[styles.content, { paddingBottom: insets.bottom + spacing['3xl'] }]}>
      <View style={styles.hero}>
        <View style={styles.doneIcon}>
          <Icon name="check" size={32} color={colors.onPrimary} />
        </View>
        <ThemedText variant="display" style={styles.center}>
          오늘 학습을 마쳤어요
        </ThemedText>
        <ThemedText variant="subhead" tone="inkSecondary" style={styles.center}>
          {results.length}개 항목을 확인했어요
        </ThemedText>
      </View>

      <View style={styles.paths}>
        {(Object.keys(pathLabel) as Path[]).map((path) => (
          <Card key={path} variant={path === 'independent' ? 'lavender' : 'surface'} style={styles.pathCard}>
            <ThemedText variant="stat">{results.filter((r) => r.path === path).length}</ThemedText>
            <ThemedText variant="caption" tone="inkSecondary">
              {pathLabel[path]}
            </ThemedText>
          </Card>
        ))}
      </View>

      {helped.length > 0 && (
        <View style={styles.section}>
          <ThemedText variant="title">도움이 필요했던 항목</ThemedText>
          {helped.map(({ question, path }) => (
            <Card key={question.id} style={styles.item}>
              <View style={styles.itemHeader}>
                <Chip label={pathLabel[path]} />
                <ThemedText variant="caption" tone="inkMuted">
                  {question.unitTitle}
                </ThemedText>
              </View>
              <ThemedText variant="subhead">{question.answerText}</ThemedText>
            </Card>
          ))}
        </View>
      )}

      <View style={styles.section}>
        <View style={styles.sectionHeader}>
          <ThemedText variant="title">다음 복습</ThemedText>
          <Chip label="예시 값" />
        </View>
        <Card style={styles.schedule}>
          {results.map(({ question, path }) => (
            <View key={question.id} style={styles.scheduleRow}>
              <ThemedText variant="subhead" numberOfLines={1} style={styles.scheduleText}>
                {question.answerText}
              </ThemedText>
              <ThemedText variant="subhead" tone="primaryInk">
                {SAMPLE_NEXT[path]}
              </ThemedText>
            </View>
          ))}
        </Card>
      </View>

      <View style={styles.section}>
        <ThemedText variant="title">지금 내 기억</ThemedText>
        <Card style={styles.schedule}>
          {units.map((unit) => (
            <Gauge key={unit.title} label={unit.title} value={unit.value} />
          ))}
        </Card>
      </View>

      <Button title="홈으로" onPress={() => router.navigate('/')} />
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  content: { padding: spacing.xl, gap: spacing['2xl'] },
  hero: { alignItems: 'center', gap: spacing.md, paddingTop: spacing.xl },
  center: { textAlign: 'center' },
  doneIcon: {
    width: 72,
    height: 72,
    borderRadius: components.iconButton.rounded,
    backgroundColor: colors.primary,
    alignItems: 'center',
    justifyContent: 'center',
  },
  paths: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.md },
  pathCard: { flexBasis: '47%', flexGrow: 1, gap: spacing.xs },
  section: { gap: spacing.md },
  sectionHeader: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  item: { gap: spacing.sm },
  itemHeader: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm },
  schedule: { gap: spacing.lg },
  scheduleRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.md },
  scheduleText: { flex: 1 },
});
