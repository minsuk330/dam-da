import { router } from 'expo-router';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Card } from '@/components/card';
import { Icon } from '@/components/icon';
import { ThemedText } from '@/components/themed-text';
import { colors, components, spacing } from '@/theme';

import { SHARE_GUIDES } from './steps';

/** 공유 링크 만드는 법 (#168): 대화를 나눈 서비스를 고르면 그 서비스의 단계 안내로 간다. */
export function ShareGuidePicker() {
  const insets = useSafeAreaInsets();
  return (
    <ScrollView
      contentInsetAdjustmentBehavior="automatic"
      contentContainerStyle={[styles.content, { paddingBottom: insets.bottom + spacing.xl }]}>
      <View style={styles.intro}>
        <ThemedText variant="display">어디서 나눈{'\n'}대화인가요?</ThemedText>
        <ThemedText variant="subhead" tone="inkSecondary">
          공유 링크를 복사해 담다에 붙여넣으면 대화를 원문 그대로 담아요.
        </ThemedText>
      </View>
      <Card style={styles.list}>
        {SHARE_GUIDES.map((guide, i) => (
          <Pressable
            key={guide.source}
            accessibilityRole="link"
            onPress={() => router.push({ pathname: '/share-guide/[source]', params: { source: guide.source } })}
            style={({ pressed }) => [styles.row, i > 0 && styles.divided, pressed && styles.rowPressed]}>
            <View style={styles.rowIcon}>
              <Icon name="link" color={colors.primaryInk} />
            </View>
            <View style={styles.rowText}>
              <ThemedText variant="headline">{guide.label}</ThemedText>
              <ThemedText variant="caption" tone="inkMuted">
                {guide.where} · {guide.steps.length}단계
              </ThemedText>
            </View>
            <Icon name="chevron-right" color={colors.inkMuted} />
          </Pressable>
        ))}
      </Card>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  content: { padding: spacing.xl, gap: spacing['2xl'] },
  intro: { gap: spacing.sm },
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
});
