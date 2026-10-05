import { router } from 'expo-router';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';

import { useConversations } from '@/api/conversations';
import { useDaily } from '@/api/daily';
import { Card } from '@/components/card';
import { Chip } from '@/components/chip';
import { Icon } from '@/components/icon';
import { SkeletonList } from '@/components/skeleton';
import { ThemedText } from '@/components/themed-text';
import { fidelityLabel, formatRelativeDay, inputPathIcon, inputPathText } from '@/labels';
import { colors, components, spacing } from '@/theme';

export function Conversations() {
  const { data, isPending, isError } = useConversations();
  // 서버의 오늘(시간 이동 반영). 오늘·어제·N일 전 계산에 쓴다.
  const serverToday = useDaily().data?.date;

  if (isPending) {
    return (
      <View style={styles.content}>
        <SkeletonList rows={4} />
      </View>
    );
  }
  if (isError) {
    return (
      <View style={styles.center}>
        <ThemedText tone="dangerInk">대화 목록을 불러오지 못했어요.</ThemedText>
      </View>
    );
  }

  // API는 오래된 것부터 돌려준다. 화면은 최근 것부터.
  const conversations = [...data].reverse();

  return (
    <ScrollView contentInsetAdjustmentBehavior="automatic" contentContainerStyle={styles.content}>
      {conversations.length === 0 ? (
        <ThemedText tone="inkMuted">아직 저장된 대화가 없어요. Claude에서 “복습에 넣어줘”라고 요청해 보세요.</ThemedText>
      ) : (
        <Card style={styles.list}>
          {conversations.map((c, i) => (
            <Pressable
              key={c.id}
              accessibilityRole="link"
              onPress={() => router.push({ pathname: '/conversations/[id]', params: { id: c.id } })}
              style={({ pressed }) => [styles.row, i > 0 && styles.divided, pressed && styles.rowPressed]}>
              <View style={styles.rowIcon}>
                <Icon name={inputPathIcon[c.inputPath]} size="md" color={colors.primaryInk} />
              </View>
              <View style={styles.rowText}>
                <View style={styles.titleRow}>
                  <ThemedText variant="headline" numberOfLines={1} style={styles.title}>
                    {c.topicHint ?? '주제 없음'}
                  </ThemedText>
                  {c.warningCount > 0 && <Chip variant="warning" label={`경고 ${c.warningCount}`} />}
                </View>
                <ThemedText variant="caption" tone="inkMuted">
                  {formatRelativeDay(c.receivedAt, serverToday)} · {inputPathText(c)} · {fidelityLabel[c.fidelity]}
                </ThemedText>
                <ThemedText variant="caption" tone="inkSecondary">
                  메시지 {c.userTurnCount}개 · 주제 {c.reviewUnitCount}개
                </ThemedText>
              </View>
              <Icon name="chevron-right" size="md" color={colors.inkMuted} />
            </Pressable>
          ))}
        </Card>
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing.xl },
  content: { padding: spacing.xl },
  list: { paddingVertical: spacing.xs },
  row: { flexDirection: 'row', alignItems: 'center', gap: spacing.md, paddingVertical: spacing.lg },
  divided: { borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.outline },
  rowPressed: { backgroundColor: components.cardPressed.backgroundColor },
  rowIcon: {
    width: components.iconCircle.size,
    height: components.iconCircle.size,
    borderRadius: components.iconCircle.rounded,
    backgroundColor: components.iconCircle.backgroundColor,
    alignItems: 'center',
    justifyContent: 'center',
  },
  rowText: { flex: 1, gap: spacing['2xs'] },
  titleRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm },
  title: { flex: 1 },
});
