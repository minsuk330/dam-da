import { router } from 'expo-router';
import { useState } from 'react';
import { ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { ApiError } from '@/api/client';
import { hourMinute, useChangeDailySettings, useDailySettings, type DailySettings } from '@/api/settings';
import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { ChoiceChip } from '@/components/choice-chip';
import { Notice } from '@/components/notice';
import { SkeletonScreen } from '@/components/skeleton';
import { ThemedText } from '@/components/themed-text';
import { components, spacing } from '@/theme';

/** 고를 수 있는 하루 학습 시간(분). 서버 범위는 1~60분이다. */
const BUDGETS = [5, 10, 15, 20, 30];
/** 통학 시간대 알림 시각. */
const TIMES = ['07:00', '07:30', '08:00', '08:30', '18:00', '18:30'];

/** 다른 곳에서 정한 값이 선택지에 없으면 그 값도 고를 수 있게 넣는다. */
function withCurrent<T>(options: T[], current: T | null, order: (a: T, b: T) => number): T[] {
  return current === null || options.includes(current) ? options : [...options, current].sort(order);
}

/** 매일 학습 설정 (스펙 §7.7, 도메인 스토리 S2-1): 하루 학습 시간과 매일 학습 알림 시각. */
export function Settings() {
  const { data, isPending, isError, refetch } = useDailySettings();

  if (isPending) {
    return <SkeletonScreen blocks={[components.cardStat.height, components.cardStat.height]} />;
  }
  if (isError) {
    return (
      <View style={styles.center}>
        <ThemedText tone="dangerInk">설정을 불러오지 못했어요.</ThemedText>
        <Button variant="secondary" title="다시 시도" onPress={() => refetch()} />
      </View>
    );
  }
  return <SettingsForm saved={data} />;
}

function SettingsForm({ saved }: { saved: DailySettings }) {
  const insets = useSafeAreaInsets();
  const change = useChangeDailySettings();
  const [budget, setBudget] = useState(saved.budgetMinutes);
  const [notifyAt, setNotifyAt] = useState(saved.notifyAt === null ? null : hourMinute(saved.notifyAt));

  const budgets = withCurrent(BUDGETS, saved.budgetMinutes, (a, b) => a - b);
  const times = withCurrent(TIMES, saved.notifyAt === null ? null : hourMinute(saved.notifyAt), (a, b) => a.localeCompare(b));
  const dirty = budget !== saved.budgetMinutes || notifyAt !== (saved.notifyAt === null ? null : hourMinute(saved.notifyAt));
  const error =
    change.error instanceof ApiError && change.error.serverMessage
      ? change.error.serverMessage
      : change.isError
        ? '설정을 저장하지 못했어요. 다시 시도해 주세요.'
        : null;

  return (
    <View style={styles.screen}>
      <ScrollView contentContainerStyle={styles.content}>
        <Card style={styles.section}>
          <View style={styles.sectionText}>
            <ThemedText variant="title">하루 학습 시간</ThemedText>
            <ThemedText variant="subhead" tone="inkSecondary">
              오늘의 학습은 이 시간 안에 끝나도록 복습과 새 항목을 골라요.
            </ThemedText>
          </View>
          <View accessibilityRole="radiogroup" accessibilityLabel="하루 학습 시간" style={styles.pills}>
            {budgets.map((minutes) => (
              <ChoiceChip key={minutes} label={`${minutes}분`} selected={budget === minutes} onPress={() => setBudget(minutes)} />
            ))}
          </View>
        </Card>

        <Card style={styles.section}>
          <View style={styles.sectionText}>
            <ThemedText variant="title">매일 학습 알림</ThemedText>
            <ThemedText variant="subhead" tone="inkSecondary">
              통학 시간에 맞춰 오늘의 학습을 알려드려요. 이미 끝낸 날은 알리지 않아요.
            </ThemedText>
          </View>
          <View accessibilityRole="radiogroup" accessibilityLabel="알림 시각" style={styles.pills}>
            {times.map((time) => (
              <ChoiceChip key={time} label={time} selected={notifyAt === time} onPress={() => setNotifyAt(time)} />
            ))}
            <ChoiceChip label="끄기" selected={notifyAt === null} onPress={() => setNotifyAt(null)} />
          </View>
        </Card>

        {error && <Notice tone="danger">{error}</Notice>}
      </ScrollView>

      <View style={[styles.footer, { paddingBottom: insets.bottom + spacing.xl }]}>
        <Button
          title="저장"
          disabled={!dirty}
          loading={change.isPending}
          onPress={() =>
            change.mutate(
              { budgetMinutes: budget, notifyAt },
              // 주소로 바로 열었으면 돌아갈 화면이 없어 홈으로 간다.
              { onSuccess: () => (router.canGoBack() ? router.back() : router.navigate('/')) },
            )
          }
        />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1 },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: spacing.md, padding: spacing.xl },
  content: { padding: spacing.xl, gap: spacing.lg },
  section: { gap: spacing.lg },
  sectionText: { gap: spacing.xs },
  pills: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
  footer: { paddingHorizontal: spacing.xl, paddingTop: spacing.md },
});
