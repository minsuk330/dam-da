import { Link } from 'expo-router';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, View } from 'react-native';

import { useConversations } from '@/api/conversations';
import { Card } from '@/components/card';
import { Chip } from '@/components/chip';
import { useTabBarSpace } from '@/components/tab-bar';
import { ThemedText } from '@/components/themed-text';
import { fidelityLabel, formatDateTime, inputPathLabel } from '@/labels';
import { colors, spacing } from '@/theme';

export function Conversations() {
  const { data, isPending, isError } = useConversations();
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
        <ThemedText tone="dangerInk">대화 목록을 불러오지 못했어요.</ThemedText>
      </View>
    );
  }

  // API는 오래된 것부터 돌려준다. 화면은 최근 것부터.
  const conversations = [...data].reverse();

  return (
    <ScrollView contentInsetAdjustmentBehavior="automatic" contentContainerStyle={[styles.content, { paddingBottom: tabBarSpace }]}>
      {conversations.length === 0 ? (
        <ThemedText tone="inkMuted">
          아직 저장된 대화가 없어요. Claude에서 “복습에 넣어줘”라고 요청해 보세요.
        </ThemedText>
      ) : (
        conversations.map((c) => (
          <Link key={c.id} href={{ pathname: '/conversations/[id]', params: { id: c.id } }} asChild>
            <Pressable accessibilityRole="link" style={({ pressed }) => pressed && styles.pressed}>
              <Card style={styles.card}>
                <View style={styles.titleRow}>
                  <ThemedText variant="headline" style={styles.title}>
                    {c.topicHint ?? '주제 없음'}
                  </ThemedText>
                  {c.warningCount > 0 && <Chip variant="warning" label={`경고 ${c.warningCount}`} />}
                </View>
                <ThemedText variant="caption" tone="inkMuted">
                  {formatDateTime(c.receivedAt)} · {inputPathLabel[c.inputPath]} · {fidelityLabel[c.fidelity]}
                </ThemedText>
                <ThemedText variant="subhead" tone="inkSecondary">
                  발화 {c.userTurnCount}개 · 복습 단위 {c.reviewUnitCount}개
                </ThemedText>
              </Card>
            </Pressable>
          </Link>
        ))
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing.xl },
  content: { padding: spacing.xl, gap: spacing.md },
  card: { gap: spacing.xs },
  titleRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm },
  title: { flex: 1 },
  pressed: { opacity: 0.85 },
});
