import { ScrollView, StyleSheet, View } from 'react-native';

import { ApiError } from '@/api/client';
import { offsetDays, useDevClock, useMoveDevClock } from '@/api/dev-clock';
import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { Notice } from '@/components/notice';
import { Skeleton } from '@/components/skeleton';
import { ThemedText } from '@/components/themed-text';
import { spacing, typography } from '@/theme';

const STEPS = [1, 3, 7];

const serverDate = new Intl.DateTimeFormat('ko-KR', {
  month: 'long',
  day: 'numeric',
  weekday: 'short',
  hour: 'numeric',
  minute: '2-digit',
});

/**
 * 시연 도구 (스펙 §11.3 시간 이동 데모): 서버 시계를 며칠 앞으로 옮겨 기억 게이지가 떨어지고 복습으로 회복하는 장면을 보여준다.
 * 로그인한 사용자면 토큰 없이 쓴다. 서버가 시연 도구를 켰을 때(DEMO_CLOCK_ENABLED)만 동작한다. 시계는 서버 전체가 함께 움직인다.
 */
export function DemoTools() {
  const clock = useDevClock();
  const move = useMoveDevClock();
  const disabled = clock.error instanceof ApiError && clock.error.status === 404;

  return (
    <ScrollView contentContainerStyle={styles.content}>
      <View style={styles.intro}>
        <ThemedText variant="title">서버 날짜 옮기기</ThemedText>
        <ThemedText variant="subhead" tone="inkSecondary">
          며칠 뒤로 옮기면 기억 게이지가 떨어지고 오늘의 학습에 복습할 내용이 생겨요. 시연이 끝나면 처음으로 되돌려 주세요.
        </ThemedText>
      </View>

      {disabled ? (
        <Notice tone="danger">이 서버에서는 시연 도구가 꺼져 있어요.</Notice>
      ) : (
        <Card style={styles.card}>
          <ThemedText variant="caption" tone="inkMuted">
            지금 서버 날짜
          </ThemedText>
          {clock.data ? (
            <View style={styles.now}>
              <ThemedText variant="title">{serverDate.format(new Date(clock.data.now))}</ThemedText>
              <ThemedText variant="subhead" tone={offsetDays(clock.data.offset) > 0 ? 'primaryInk' : 'inkMuted'}>
                {offsetDays(clock.data.offset) > 0 ? `${offsetDays(clock.data.offset)}일 앞으로 옮김` : '실제 날짜'}
              </ThemedText>
            </View>
          ) : clock.isError ? (
            <ThemedText tone="dangerInk">서버 날짜를 불러오지 못했어요.</ThemedText>
          ) : (
            <Skeleton width="60%" height={typography.title.lineHeight} />
          )}
        </Card>
      )}

      {!disabled && (
        <View style={styles.actions}>
          <View style={styles.steps}>
            {STEPS.map((days) => (
              <Button
                key={days}
                variant="secondary"
                title={`+${days}일`}
                disabled={!clock.data || move.isPending}
                onPress={() => move.mutate(days)}
                style={styles.step}
              />
            ))}
          </View>
          <Button
            variant="secondary"
            title="실제 날짜로 되돌리기"
            disabled={!clock.data || move.isPending || offsetDays(clock.data.offset) === 0}
            loading={move.isPending && move.variables === null}
            onPress={() => move.mutate(null)}
          />
          {move.isError && <Notice tone="danger">날짜를 옮기지 못했어요. 다시 시도해 주세요.</Notice>}
        </View>
      )}

      <Notice>서버 시계는 모든 사용자에게 함께 적용돼요. 시연용 서버에서만 쓰세요.</Notice>

    </ScrollView>
  );
}

const styles = StyleSheet.create({
  content: { padding: spacing.xl, gap: spacing.lg },
  intro: { gap: spacing.xs },
  card: { gap: spacing.md },
  now: { gap: spacing['2xs'] },
  actions: { gap: spacing.sm },
  steps: { flexDirection: 'row', gap: spacing.sm },
  step: { flex: 1 },
});
