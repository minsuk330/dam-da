import { router } from 'expo-router';
import { ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { Chip } from '@/components/chip';
import { Icon } from '@/components/icon';
import { ThemedText } from '@/components/themed-text';
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

export type ItemResult = { questionId: number; stem: string; path: Path };

// 다음 복습 일정은 FSRS가 계산한다. 첫 학습 요약 API 연결(#58) 전까지 경로별 예시 값.
const SAMPLE_NEXT: Record<Path, string> = {
  independent: '4일 뒤',
  afterHint: '2일 뒤',
  afterExplanation: '내일',
  repeatedWrong: '오늘 한 번 더',
  held: '다음 학습에서',
};

/** 학습 완료: 확인한 항목, 도움이 필요했던 항목, 다음 복습 일정. */
export function ReviewComplete({ results }: { results: ItemResult[] }) {
  const insets = useSafeAreaInsets();
  const helped = results.filter((r) => r.path !== 'independent');

  return (
    <ScrollView contentContainerStyle={[styles.content, { paddingBottom: insets.bottom + spacing['3xl'] }]}>
      <View style={styles.hero}>
        <View style={styles.doneIcon}>
          <Icon name="check" size="xl" color={colors.onPrimary} />
        </View>
        <ThemedText variant="display" style={styles.center}>
          학습을 마쳤어요
        </ThemedText>
        <ThemedText variant="subhead" tone="inkSecondary" style={styles.center}>
          {results.length}문제를 풀었어요
        </ThemedText>
      </View>

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

      {helped.length > 0 && (
        <View style={styles.section}>
          <ThemedText variant="title">도움이 필요했던 항목</ThemedText>
          <Card style={styles.schedule}>
            {helped.map(({ questionId, stem, path }, i) => (
              <View key={questionId} style={[styles.item, i > 0 && styles.divided]}>
                <View style={styles.itemHeader}>
                  <Chip label={pathLabel[path]} />
                </View>
                <ThemedText variant="subhead">{stem}</ThemedText>
              </View>
            ))}
          </Card>
        </View>
      )}

      <View style={styles.section}>
        <View style={styles.sectionHeader}>
          <ThemedText variant="title">다음 복습</ThemedText>
          <Chip label="예시 값" />
        </View>
        <Card style={styles.schedule}>
          {results.map(({ questionId, stem, path }) => (
            <View key={questionId} style={styles.scheduleRow}>
              <ThemedText variant="subhead" numberOfLines={1} style={styles.scheduleText}>
                {stem}
              </ThemedText>
              <ThemedText variant="subhead" tone="primaryInk">
                {SAMPLE_NEXT[path]}
              </ThemedText>
            </View>
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
    width: components.doneMark.size,
    height: components.doneMark.size,
    borderRadius: components.doneMark.rounded,
    backgroundColor: components.doneMark.backgroundColor,
    alignItems: 'center',
    justifyContent: 'center',
  },
  paths: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.md },
  pathCard: { flexBasis: '47%', flexGrow: 1, gap: spacing.xs },
  section: { gap: spacing.md },
  sectionHeader: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  item: { gap: spacing.sm },
  divided: { borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.outline, paddingTop: spacing.lg },
  itemHeader: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm },
  schedule: { gap: spacing.lg },
  scheduleRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.md },
  scheduleText: { flex: 1 },
});
