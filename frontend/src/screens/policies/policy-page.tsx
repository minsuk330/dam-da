import { ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/themed-text';
import { spacing } from '@/theme';

import type { Policy } from './content';

/** 개인정보 처리방침·이용약관 본문. 로그인 전후 모두 열리며 웹 공개 주소로도 들어온다(#148). */
export function PolicyPage({ policy }: { policy: Policy }) {
  const insets = useSafeAreaInsets();
  return (
    <ScrollView contentContainerStyle={[styles.content, { paddingBottom: insets.bottom + spacing['3xl'] }]}>
      {/* 제목은 화면 헤더가 보여준다. */}
      <View style={styles.header}>
        <ThemedText variant="caption" tone="inkMuted">
          시행일 {policy.effectiveDate}
        </ThemedText>
        <ThemedText tone="inkSecondary">{policy.intro}</ThemedText>
      </View>
      {policy.sections.map((section) => (
        <View key={section.heading} style={styles.section}>
          <ThemedText variant="headline" accessibilityRole="header">
            {section.heading}
          </ThemedText>
          {section.body.map((paragraph) => (
            <ThemedText key={paragraph} tone="inkSecondary">
              {paragraph}
            </ThemedText>
          ))}
        </View>
      ))}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  content: { paddingHorizontal: spacing.xl, paddingTop: spacing.lg, gap: spacing['2xl'] },
  header: { gap: spacing.sm },
  section: { gap: spacing.sm },
});
