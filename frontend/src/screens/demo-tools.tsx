import { useState } from 'react';
import { ScrollView, StyleSheet, TextInput, View } from 'react-native';

import { ApiError } from '@/api/client';
import { offsetDays, useDevClock, useDevToken, useMoveDevClock } from '@/api/dev-clock';
import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { Notice } from '@/components/notice';
import { Skeleton } from '@/components/skeleton';
import { ThemedText } from '@/components/themed-text';
import { components, spacing, typography } from '@/theme';

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
 * 개발 도구가 켜진 서버에서만 동작하고, 배포 서버는 시연자가 입력한 토큰이 맞아야 한다. 시계는 서버 전체가 함께 움직인다.
 */
export function DemoTools() {
  const { token, save, clear } = useDevToken();
  const clock = useDevClock(token);
  const move = useMoveDevClock(token);
  const denied = clock.error instanceof ApiError && (clock.error.status === 404 || clock.error.status === 403);
  const askToken = (!clock.isFetching && clock.data === undefined && !clock.isError) || denied;

  return (
    <ScrollView contentContainerStyle={styles.content}>
      <View style={styles.intro}>
        <ThemedText variant="title">서버 날짜 옮기기</ThemedText>
        <ThemedText variant="subhead" tone="inkSecondary">
          며칠 뒤로 옮기면 기억 게이지가 떨어지고 오늘의 학습에 복습할 내용이 생겨요. 시연이 끝나면 처음으로 되돌려 주세요.
        </ThemedText>
      </View>

      {askToken ? (
        <TokenForm denied={denied} onSave={save} />
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

      {!askToken && (
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

      {token && <Button variant="secondary" title="저장한 토큰 지우기" onPress={clear} />}
    </ScrollView>
  );
}

/** 개발 도구 토큰 입력. 토큰은 이 브라우저에만 저장하고 코드·번들에는 넣지 않는다. */
function TokenForm({ denied, onSave }: { denied: boolean; onSave: (token: string) => void }) {
  const [value, setValue] = useState('');
  return (
    <Card style={styles.card}>
      <ThemedText variant="headline">개발 도구 토큰</ThemedText>
      <ThemedText variant="subhead" tone="inkSecondary">
        서버의 DEV_TOOLS_TOKEN을 입력해 주세요. 이 브라우저에만 저장돼요.
      </ThemedText>
      <TextInput
        accessibilityLabel="개발 도구 토큰"
        value={value}
        onChangeText={setValue}
        secureTextEntry
        autoCapitalize="none"
        autoCorrect={false}
        placeholder="토큰"
        placeholderTextColor={components.fieldPlaceholder.textColor}
        style={styles.field}
      />
      {denied && <Notice tone="danger">토큰이 맞지 않거나 이 서버에서 개발 도구가 꺼져 있어요.</Notice>}
      <Button title="저장" disabled={value.trim().length === 0} onPress={() => onSave(value.trim())} />
    </Card>
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
  field: {
    ...components.field.typography,
    color: components.field.textColor,
    backgroundColor: components.field.backgroundColor,
    borderRadius: components.field.rounded,
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.md,
  },
});
