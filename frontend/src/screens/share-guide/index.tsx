import { router } from 'expo-router';
import { useState } from 'react';
import { Image, ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { ChoiceChip } from '@/components/choice-chip';
import { Icon } from '@/components/icon';
import { ThemedText } from '@/components/themed-text';
import { colors, components, spacing } from '@/theme';

import { SHARE_GUIDES, type ShareSource } from './steps';

/**
 * 공유 링크 만드는 법 (#168). 서비스를 고르면 공유 버튼부터 링크 복사까지 단계별로 보여주고, 추가 탭으로 돌려보낸다.
 * 추가 탭 안내 문구, 링크 수집 실패, 처음 사용자 홈에서 들어온다.
 */
export function ShareGuide({ initialSource }: { initialSource?: ShareSource }) {
  const insets = useSafeAreaInsets();
  const [source, setSource] = useState<ShareSource>(initialSource ?? 'chatgpt');
  const guide = SHARE_GUIDES.find((g) => g.source === source) ?? SHARE_GUIDES[0];

  return (
    <ScrollView
      contentInsetAdjustmentBehavior="automatic"
      contentContainerStyle={[styles.content, { paddingBottom: insets.bottom + spacing.xl }]}>
      <View style={styles.intro}>
        <ThemedText variant="title">대화를 나눈 서비스를 골라요</ThemedText>
        <ThemedText variant="subhead" tone="inkSecondary">
          공유 링크를 복사해 담다에 붙여넣으면 대화를 원문 그대로 담아요.
        </ThemedText>
      </View>

      <View accessibilityRole="radiogroup" style={styles.sources}>
        {SHARE_GUIDES.map((g) => (
          <ChoiceChip
            key={g.source}
            label={g.label}
            selected={g.source === source}
            onPress={() => setSource(g.source)}
            style={styles.source}
          />
        ))}
      </View>

      <Card style={styles.steps}>
        <ThemedText variant="caption" tone="inkMuted">
          {guide.where} 기준
        </ThemedText>
        {guide.steps.map((step, i) => (
          <View key={step.title} style={[styles.step, i > 0 && styles.divided]}>
            <View style={styles.stepRow}>
              <View style={styles.stepNumber}>
                <ThemedText variant="headline" tone="primaryInk">
                  {i + 1}
                </ThemedText>
              </View>
              <View style={styles.stepText}>
                <ThemedText variant="headline">{step.title}</ThemedText>
                <ThemedText variant="subhead" tone="inkSecondary">
                  {step.detail}
                </ThemedText>
              </View>
            </View>
            {step.image && (
              <Image
                source={step.image}
                accessibilityLabel={`${guide.label} ${i + 1}단계 화면`}
                resizeMode="contain"
                style={styles.stepImage}
              />
            )}
          </View>
        ))}
        <View style={[styles.step, styles.divided]}>
          <View style={styles.stepRow}>
            <View style={styles.stepNumber}>
              <ThemedText variant="headline" tone="primaryInk">
                {guide.steps.length + 1}
              </ThemedText>
            </View>
            <View style={styles.stepText}>
              <ThemedText variant="headline">담다에 붙여넣어요</ThemedText>
              <ThemedText variant="subhead" tone="inkSecondary">
                추가 탭의 공유 링크 칸에 붙여넣어요. 이런 모양이면 맞아요.
              </ThemedText>
              <View style={styles.linkExample}>
                <Icon name="link" size="sm" color={colors.inkSecondary} />
                <ThemedText variant="caption" tone="inkSecondary" style={styles.linkText}>
                  {guide.linkExample}
                </ThemedText>
              </View>
            </View>
          </View>
        </View>
      </Card>

      <View style={styles.notes}>
        {guide.note && <Note text={guide.note} />}
        <Note text="링크가 있으면 누구나 그 대화를 볼 수 있어요. 담은 뒤에는 공유를 꺼도 괜찮아요." />
      </View>

      <Button title="링크 붙여넣으러 가기" onPress={() => router.navigate('/add')} />
    </ScrollView>
  );
}

function Note({ text }: { text: string }) {
  return (
    <View style={styles.note}>
      <Icon name="info" size="sm" color={colors.inkMuted} />
      <ThemedText variant="caption" tone="inkSecondary" style={styles.noteText}>
        {text}
      </ThemedText>
    </View>
  );
}

const styles = StyleSheet.create({
  content: { padding: spacing.xl, gap: spacing['2xl'] },
  intro: { gap: spacing.xs },
  sources: { flexDirection: 'row', gap: spacing.sm },
  source: { flex: 1 },
  steps: { paddingTop: spacing.lg },
  step: { gap: spacing.md, paddingVertical: spacing.lg },
  divided: { borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.outline },
  stepRow: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing.md },
  stepNumber: {
    width: components.iconCircle.size,
    height: components.iconCircle.size,
    borderRadius: components.iconCircle.rounded,
    backgroundColor: components.iconCircle.backgroundColor,
    alignItems: 'center',
    justifyContent: 'center',
  },
  stepText: { flex: 1, gap: spacing['2xs'] },
  // 캡처 크기는 받은 뒤 DESIGN.md에 토큰으로 정한다(#168). 그때까지는 카드 폭에 맞춰 세로로 늘인다.
  stepImage: { width: '100%', aspectRatio: 9 / 16, borderRadius: components.field.rounded },
  linkExample: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.xs,
    marginTop: spacing.xs,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    borderRadius: components.field.rounded,
    backgroundColor: components.field.backgroundColor,
  },
  linkText: { flex: 1 },
  notes: { gap: spacing.sm },
  note: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing.sm },
  noteText: { flex: 1 },
});
