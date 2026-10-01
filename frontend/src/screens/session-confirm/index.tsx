import { router } from 'expo-router';
import { useRef, useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import type { Schemas } from '@/api/client';
import { useFirstStudy, useLearningSession } from '@/api/learning-sessions';
import { Button } from '@/components/button';
import { ThemedText } from '@/components/themed-text';
import { colors, spacing } from '@/theme';

import { GoalsStep } from './goals-step';
import { PrepareStep } from './prepare-step';
import { TurnsStep } from './turns-step';
import { UnitsStep } from './units-step';

type Step = 'turns' | 'units' | 'goals' | 'prepare';
type Status = Schemas['LearningSessionDetail']['status'];

/** 세션 상태로 들어갈 단계를 정한다. 확인을 마친 세션은 내용을 고칠 수 없으므로 1·2단계로 돌아가지 않는다. */
function stepOf(status: Status): Step | null {
  switch (status) {
    case 'RECEIVED':
    case 'REVIEWING':
      return null;
    case 'AWAITING_CONFIRMATION':
      return 'turns';
    case 'CONFIRMED':
      return 'goals';
    case 'QUESTIONS_READY':
    case 'IN_PROGRESS':
      return 'prepare';
  }
}

/** 세션 확인 (도메인 스토리 S1-8 ~ S1-10): 발화 확인 → 복습 단위 확인 → 학습 목표·기억 강도 → 문제 준비. */
export function SessionConfirm({ id }: { id: number }) {
  const insets = useSafeAreaInsets();
  const scroll = useRef<ScrollView>(null);
  const { data, isPending, isError, refetch } = useLearningSession(id);
  const [chosenStep, setChosenStep] = useState<Step | null>(null);
  // 확인을 마친 세션은 목표를 이미 골라 문제를 만드는 중일 수 있다. 다시 들어와도(새로고침 포함) 목표 단계로 돌아가 다시 고르지 않게
  // 생성 상태를 보고 준비 단계로 보낸다. 다시 고르면 생성이 처음부터 다시 돈다.
  const confirmed = data?.status === 'CONFIRMED';
  const plan = useFirstStudy(id, confirmed && chosenStep === null);

  if (isPending || (confirmed && chosenStep === null && plan.isPending)) {
    return (
      <View style={styles.center}>
        <ActivityIndicator color={colors.primary} />
      </View>
    );
  }
  if (isError) {
    return (
      <View style={styles.center}>
        <ThemedText tone="dangerInk">학습 세션을 불러오지 못했어요.</ThemedText>
        <Button variant="secondary" title="다시 시도" onPress={() => refetch()} />
      </View>
    );
  }

  const resumePrepare = confirmed && (plan.data?.generating || plan.data?.failed);
  const step = chosenStep ?? (resumePrepare ? 'prepare' : stepOf(data.status));
  if (step === null) {
    return (
      <View style={styles.center}>
        <ActivityIndicator color={colors.primary} />
        <ThemedText variant="headline">내용을 검수하고 있어요</ThemedText>
        <ThemedText variant="subhead" tone="inkMuted" style={styles.centerText}>
          대화에서 뽑은 내용이 맞는지 확인하는 중이에요. 끝나면 알림으로 알려드릴게요.
        </ThemedText>
        <Button variant="secondary" title="새로고침" onPress={() => refetch()} />
      </View>
    );
  }

  function go(next: Step) {
    setChosenStep(next);
    scroll.current?.scrollTo({ y: 0, animated: false });
  }

  return (
    <ScrollView
      ref={scroll}
      keyboardShouldPersistTaps="handled"
      contentContainerStyle={[styles.content, { paddingBottom: insets.bottom + spacing['3xl'] }]}>
      <View style={styles.topRow}>
        <ThemedText variant="caption" tone="inkMuted" numberOfLines={1} style={styles.topic}>
          {data.topicHint ?? '주제 없음'}
        </ThemedText>
        <Pressable
          accessibilityRole="link"
          hitSlop={8}
          onPress={() => router.push({ pathname: '/conversations/[id]', params: { id: data.conversationId } })}>
          <ThemedText variant="caption" tone="primaryInk">
            원본 대화 보기
          </ThemedText>
        </Pressable>
      </View>
      {step === 'turns' && <TurnsStep session={data} onNext={() => go('units')} />}
      {step === 'units' && <UnitsStep session={data} onBack={() => go('turns')} onConfirmed={() => go('goals')} />}
      {step === 'goals' && <GoalsStep sessionId={id} onChosen={() => go('prepare')} />}
      {step === 'prepare' && <PrepareStep sessionId={id} onNoPlan={() => go('goals')} />}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: spacing.md, padding: spacing.xl },
  centerText: { textAlign: 'center' },
  content: { padding: spacing.xl, gap: spacing.sm },
  topRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.md },
  topic: { flex: 1 },
});
