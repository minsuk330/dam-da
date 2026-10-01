import { ScrollView, StyleSheet, View } from 'react-native';

import { ApiError, type Schemas } from '@/api/client';
import { useConversation } from '@/api/conversations';
import { useLearningSessions } from '@/api/learning-sessions';
import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { Chip } from '@/components/chip';
import { Skeleton, SkeletonList } from '@/components/skeleton';
import { ThemedText } from '@/components/themed-text';
import { aiVerdictLabel, factKindLabel, fidelityLabel, formatDateTime, inputPathLabel, intentLabel } from '@/labels';
import { openSession } from '@/navigation';
import { colors, components, opacity, radius, spacing, typography } from '@/theme';

type Turn = Schemas['UserTurnResponse'];
type ReviewUnit = Schemas['ReviewUnitResponse'];

export function ConversationDetail({ id }: { id: string }) {
  const { data, isPending, error } = useConversation(id);
  const sessions = useLearningSessions();

  if (isPending) {
    return (
      <View style={styles.content}>
        <View style={styles.header}>
          <Skeleton width="70%" height={typography.display.lineHeight} />
          <Skeleton width="50%" />
        </View>
        <Skeleton shape="block" height={components.cardStat.height} />
        <SkeletonList rows={3} />
      </View>
    );
  }
  if (error) {
    const notFound = error instanceof ApiError && error.status === 404;
    return (
      <View style={styles.center}>
        <ThemedText tone="dangerInk">{notFound ? '대화를 찾을 수 없어요.' : '대화를 불러오지 못했어요.'}</ThemedText>
      </View>
    );
  }

  const turnsByIndex = new Map(data.userTurns.map((t) => [t.index, t]));
  const sessionId = data.learningSessionId;
  const session = sessions.data?.find((s) => s.id === sessionId);

  return (
    <ScrollView contentInsetAdjustmentBehavior="automatic" contentContainerStyle={styles.content}>
      <View style={styles.header}>
        <ThemedText variant="display">{data.topicHint ?? '주제 없음'}</ThemedText>
        <ThemedText variant="caption" tone="inkMuted">
          {formatDateTime(data.receivedAt)} · {inputPathLabel[data.inputPath]} · {fidelityLabel[data.fidelity]}
        </ThemedText>
      </View>

      {sessionId !== null && (
        <Button
          title={session?.status === 'IN_PROGRESS' ? '기억 상태 보기' : '학습 내용 확인하기'}
          onPress={() => openSession(sessionId, session?.status)}
        />
      )}

      {data.fidelity === 'model_transcribed' && (
        <View style={styles.notice}>
          <ThemedText variant="subhead" tone="primaryInk">
            커넥터로 받은 메시지는 Claude가 옮겨 적은 것이라 실제로 입력한 문장과 다를 수 있어요. 내용이 맞는지 확인해 주세요.
          </ThemedText>
        </View>
      )}

      {/* 저장 시 경고(data.warnings)는 커넥터 스키마 기준의 진단 문장이라 화면에 내지 않는다. 고칠 일은 학습 내용 확인 단계가
          사용자 말로 보여 주고, 원문 경고는 개발 도구 세션 뷰어에서 본다. */}
      <View style={styles.section}>
        <ThemedText variant="title">주제 {data.reviewUnits.length}개</ThemedText>
        {data.reviewUnits.map((unit) => (
          <ReviewUnitCard key={unit.title} unit={unit} turnsByIndex={turnsByIndex} />
        ))}
      </View>

      <View style={styles.section}>
        <ThemedText variant="title">받은 메시지 {data.userTurns.length}개</ThemedText>
        <Card style={styles.list}>
          {data.userTurns.map((turn, i) => (
            <TurnRow key={turn.index} turn={turn} divided={i > 0} />
          ))}
        </Card>
      </View>
    </ScrollView>
  );
}

function ReviewUnitCard({ unit, turnsByIndex }: { unit: ReviewUnit; turnsByIndex: Map<number, Turn> }) {
  return (
    <Card style={styles.cardGap}>
      <ThemedText variant="headline">{unit.title}</ThemedText>
      {unit.keyPoints.map((p) => (
        <View key={p.point} style={styles.row}>
          {factKindLabel[p.kind] && <Chip variant={p.kind === 'warning' ? 'warning' : 'soft'} label={factKindLabel[p.kind]!} />}
          <ThemedText style={styles.flex}>
            {p.point} <TurnRefs turns={p.turns} />
          </ThemedText>
        </View>
      ))}
      {unit.confusionPoints.map((c) => {
        const correction = turnsByIndex.get(c.turn)?.correction;
        return (
          <View key={c.turn} style={styles.confusion}>
            <Chip variant="warning" label="헷갈렸던 점" />
            <ThemedText variant="subhead">
              {c.userBelief} <TurnRefs turns={[c.turn]} />
            </ThemedText>
            {correction && (
              <ThemedText variant="subhead" tone="inkSecondary">
                AI가 바로잡은 내용: {correction}
              </ThemedText>
            )}
          </View>
        );
      })}
    </Card>
  );
}

/** 받은 메시지 한 줄. 대화 진행 메시지는 흐리게 두고 복습에 쓰지 않는다고 밝힌다. */
function TurnRow({ turn, divided }: { turn: Turn; divided: boolean }) {
  const verdict = aiVerdictLabel[turn.aiVerdict];
  const isMeta = turn.intent === 'meta';
  return (
    <View style={[styles.turn, divided && styles.divided, isMeta && styles.muted]}>
      <View style={styles.chips}>
        <ThemedText variant="caption" tone="inkMuted">
          {turn.index}번째 메시지
        </ThemedText>
        <Chip variant="soft" label={intentLabel[turn.intent]} />
        {isMeta && (
          <ThemedText variant="caption" tone="inkMuted">
            복습에 쓰지 않음
          </ThemedText>
        )}
      </View>
      {turn.quotedText && (
        <View style={styles.quote}>
          <ThemedText variant="subhead" tone="inkMuted">
            {turn.quotedText}
          </ThemedText>
        </View>
      )}
      <ThemedText>{turn.text}</ThemedText>
      {verdict && (
        <ThemedText variant="caption" tone="inkSecondary">
          대화에서 AI가 {verdict}
          {turn.correction ? ` · ${turn.correction}` : ''}
        </ThemedText>
      )}
    </View>
  );
}

function TurnRefs({ turns }: { turns: number[] }) {
  return (
    <ThemedText variant="caption" tone="inkMuted">
      메시지 {turns.join(', ')}
    </ThemedText>
  );
}

const styles = StyleSheet.create({
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing.xl },
  content: { padding: spacing.xl, gap: spacing['2xl'] },
  header: { gap: spacing.xs },
  notice: {
    backgroundColor: colors.primaryTint,
    borderRadius: radius.md,
    borderCurve: 'continuous',
    padding: spacing.lg,
  },
  section: { gap: spacing.md },
  cardGap: { gap: spacing.sm },
  row: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing.sm },
  flex: { flex: 1 },
  confusion: {
    gap: spacing.xs,
    backgroundColor: colors.surfaceSoft,
    borderRadius: radius.sm,
    borderCurve: 'continuous',
    padding: spacing.md,
  },
  chips: { flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', gap: spacing.sm },
  quote: { borderLeftWidth: 2, borderLeftColor: colors.outline, paddingLeft: spacing.md },
  muted: { opacity: opacity.excluded },
  list: { paddingVertical: spacing.xs },
  turn: { gap: spacing.sm, paddingVertical: spacing.lg },
  divided: { borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.outline },
});
