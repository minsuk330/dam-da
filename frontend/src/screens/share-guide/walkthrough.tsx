import { router, Stack } from 'expo-router';
import { useState } from 'react';
import { Image, Linking, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Button } from '@/components/button';
import { Chip } from '@/components/chip';
import { Icon } from '@/components/icon';
import { ThemedText } from '@/components/themed-text';
import { colors, components, radius, spacing } from '@/theme';
import { inToss } from '@/toss';

import type { ShareGuide } from './steps';

/**
 * 서비스 하나의 공유 링크 만드는 법 (#168). 한 화면에 한 단계: 진행 막대 → 화면 캡처 → STEP 칩·제목·설명 → 다음.
 * 마지막 단계(담다에 붙여넣기)에서 추가 탭으로 돌아간다. 건너뛰기도 추가 탭으로 간다.
 */
export function ShareGuideWalkthrough({ guide }: { guide: ShareGuide }) {
  const insets = useSafeAreaInsets();
  const [index, setIndex] = useState(0);
  const step = guide.steps[index];
  const last = index === guide.steps.length - 1;

  return (
    <View style={styles.screen}>
      <Stack.Screen options={{ title: `${guide.label} 공유 링크 만들기` }} />
      <View
        accessible
        accessibilityRole="progressbar"
        accessibilityLabel="안내 진행"
        accessibilityValue={{ min: 1, max: guide.steps.length, now: index + 1 }}
        style={styles.progressTrack}>
        <View style={[styles.progressFill, { width: `${Math.round(((index + 1) / guide.steps.length) * 100)}%` }]} />
      </View>

      <ScrollView contentContainerStyle={styles.content}>
        <Pressable accessibilityRole="link" hitSlop={8} onPress={() => router.navigate('/add')} style={styles.skip}>
          <ThemedText variant="subhead" tone="inkMuted">
            건너뛰기
          </ThemedText>
        </Pressable>

        {step.image ? (
          <View style={[styles.shot, { aspectRatio: step.image.aspectRatio }]}>
            <Image
              source={step.image.source}
              accessibilityLabel={`${guide.label} ${step.title} 화면`}
              resizeMode="contain"
              style={styles.shotImage}
            />
          </View>
        ) : (
          <View style={[styles.shot, styles.shotPlaceholder]}>
            <View style={styles.shotIcon}>
              <Icon name={step.icon} size="xl" color={colors.primaryInk} />
            </View>
          </View>
        )}

        <View style={styles.text}>
          <Chip label={`STEP ${String(index + 1).padStart(2, '0')}`} />
          <ThemedText variant="title">{step.title}</ThemedText>
          <ThemedText variant="body" tone="inkSecondary">
            {step.detail.map((part, i) =>
              typeof part === 'string' ? (
                part
              ) : (
                <ThemedText key={i} variant="headline">
                  {part.strong}
                </ThemedText>
              ),
            )}
          </ThemedText>
          {last && (
            <ThemedText variant="caption" tone="inkMuted">
              링크가 있으면 누구나 그 대화를 볼 수 있어요. 담은 뒤에는 공유를 꺼도 괜찮아요.
            </ThemedText>
          )}
        </View>
      </ScrollView>

      <View style={[styles.footer, { paddingBottom: insets.bottom + spacing.xl }]}>
        {/* 토스 인앱에서는 외부 서비스로 나가는 링크를 두지 않는다(#149). */}
        {!last && !inToss && (
          <Pressable accessibilityRole="link" hitSlop={8} onPress={() => Linking.openURL(guide.openUrl)} style={styles.open}>
            <ThemedText variant="headline">{guide.label} 열기</ThemedText>
          </Pressable>
        )}
        <Button
          title={last ? '링크 붙여넣으러 가기' : '다음'}
          onPress={() => (last ? router.navigate('/add') : setIndex(index + 1))}
        />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.canvas },
  progressTrack: {
    height: components.gauge.height,
    marginHorizontal: spacing.xl,
    borderRadius: components.gauge.rounded,
    backgroundColor: components.gauge.backgroundColor,
    overflow: 'hidden',
  },
  progressFill: { height: '100%', backgroundColor: components.gaugeFill.backgroundColor },
  content: { flexGrow: 1, padding: spacing.xl, gap: spacing['2xl'] },
  skip: { alignSelf: 'flex-end' },
  // 캡처 카드: 흰 면 + 1px outline(배경과 구분), 그림자 없음. 캡처는 원본 비율, 캡처가 없으면 정사각형.
  shot: {
    overflow: 'hidden',
    borderRadius: radius.lg,
    borderCurve: 'continuous',
    borderWidth: components.answerOptionOutline.width,
    borderColor: colors.outline,
    backgroundColor: colors.surface,
  },
  shotPlaceholder: { aspectRatio: 1, alignItems: 'center', justifyContent: 'center' },
  shotImage: { width: '100%', height: '100%' },
  shotIcon: {
    width: components.doneMark.size,
    height: components.doneMark.size,
    borderRadius: components.doneMark.rounded,
    backgroundColor: components.iconCircle.backgroundColor,
    alignItems: 'center',
    justifyContent: 'center',
  },
  text: { gap: spacing.sm },
  footer: { gap: spacing.lg, paddingHorizontal: spacing.xl, paddingTop: spacing.md },
  open: { alignSelf: 'center' },
});
