import { useState } from 'react';
import { Pressable, StyleSheet, TextInput, View } from 'react-native';

import type { Schemas } from '@/api/client';
import { useEditLearningSession } from '@/api/learning-sessions';
import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { Notice } from '@/components/notice';
import { Chip } from '@/components/chip';
import { ChoiceChip } from '@/components/choice-chip';
import { OptionRow } from '@/components/option-row';
import { ThemedText } from '@/components/themed-text';
import { aiVerdictLabel, intentLabel, itemKindLabel } from '@/labels';
import { colors, components, spacing } from '@/theme';

import { StepHeader } from './parts';

type Detail = Schemas['LearningSessionDetail'];
type Turn = Schemas['Turn'];
type Intent = Turn['intent'];
type Item = Schemas['Item'];

const INTENTS = Object.keys(intentLabel) as Intent[];

/** 1단계: 대화를 맞게 옮겼는지 확인한다. 발화를 고치거나, 대화 진행(meta)으로 바꾸거나, 빠진 발화를 넣는다. */
export function TurnsStep({ session, onNext }: { session: Detail; onNext: () => void }) {
  const edit = useEditLearningSession(session.id);
  const [editing, setEditing] = useState<number | 'new' | null>(null);
  const lastIndex = session.turns.at(-1)?.index ?? 0;
  // 빠진 발화를 근거로 붙일 수 있는 항목: 제외되지 않은 단위의 항목 중 포함된 것과, 근거를 잃어 자동 제외된 것.
  // 후자는 근거를 되살린 뒤 2단계에서 다시 넣을 수 있다.
  const sourceCandidates = session.units
    .filter((unit) => !unit.excluded)
    .flatMap((unit) => unit.items.filter((item) => item.status !== 'EXCLUDED' || item.sourceTurns.length === 0));

  return (
    <View style={styles.step}>
      <StepHeader
        step={1}
        total={3}
        title="대화를 맞게 옮겼는지 확인해요"
        description="내가 한 말만 보여드려요. 다르게 옮겨졌으면 고쳐 주세요."
      />

      {session.fidelity === 'model_transcribed' && (
        <Notice>Claude가 옮겨 적은 대화예요. 내가 한 말과 같은지 확인해 주세요. 확인하기 전에는 원문 그대로로 보지 않아요.</Notice>
      )}
      {edit.error && <Notice tone="danger">{edit.error.message}</Notice>}

      <Card style={styles.list}>
        {session.turns.map((turn, i) =>
          editing === turn.index ? (
            <TurnEditor
              key={turn.index}
              inline
              divided={i > 0}
            title={`${turn.index}번째 메시지 고치기`}
            initial={turn}
            saving={edit.isPending}
            onCancel={() => setEditing(null)}
            onSave={(text, intent) =>
              edit.mutate(
                {
                  kind: 'turn',
                  index: turn.index,
                  content: { text, intent, aiVerdict: turn.aiVerdict, correction: turn.correction },
                },
                { onSuccess: () => setEditing(null) },
              )
            }
          />
        ) : (
            <TurnRow
              key={turn.index}
              turn={turn}
              divided={i > 0}
              onEdit={() => setEditing(turn.index)}
              disabled={editing !== null}
            />
          ),
        )}
      </Card>

      {editing === 'new' ? (
        <TurnEditor
          title="빠진 메시지 추가"
          initial={{ text: '', intent: 'info_request' }}
          sourceCandidates={sourceCandidates}
          saving={edit.isPending}
          onCancel={() => setEditing(null)}
          onSave={(text, intent, sourceOf) =>
            edit.mutate(
              { kind: 'insert', afterIndex: lastIndex, content: { text, intent }, sourceOf },
              { onSuccess: () => setEditing(null) },
            )
          }
        />
      ) : (
        <Button
          variant="secondary"
          title="빠진 메시지 추가"
          disabled={editing !== null}
          onPress={() => setEditing('new')}
        />
      )}

      <Button title="다음: 복습할 내용 확인" disabled={editing !== null} onPress={onNext} />
    </View>
  );
}

/** 메시지 한 줄. 대화 진행(meta) 메시지는 흐리게 두고 복습에 쓰지 않는다고 밝힌다. */
function TurnRow({
  turn,
  divided,
  onEdit,
  disabled,
}: {
  turn: Turn;
  divided: boolean;
  onEdit: () => void;
  disabled: boolean;
}) {
  const meta = turn.intent === 'meta';
  const verdict = turn.aiVerdict ? aiVerdictLabel[turn.aiVerdict] : null;
  return (
    <View style={[styles.turn, divided && styles.divided]}>
      <View style={styles.turnHeader}>
        <ThemedText variant="caption" tone="inkMuted">
          {turn.index}번째 메시지
        </ThemedText>
        <Chip label={intentLabel[turn.intent]} />
        <View style={styles.spacer} />
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={`${turn.index}번째 메시지 고치기`}
          disabled={disabled}
          onPress={onEdit}
          hitSlop={8}>
          <ThemedText variant="subhead" tone={disabled ? 'inkMuted' : 'primaryInk'}>
            고치기
          </ThemedText>
        </Pressable>
      </View>
      <ThemedText tone={meta ? 'inkMuted' : 'ink'}>{turn.text}</ThemedText>
      {meta && (
        <ThemedText variant="caption" tone="inkMuted">
          대화 진행 메시지는 복습에 쓰지 않아요.
        </ThemedText>
      )}
      {verdict && (
        <ThemedText variant="caption" tone="inkSecondary">
          대화에서 AI가 {verdict}
          {turn.correction ? ` · ${turn.correction}` : ''}
        </ThemedText>
      )}
    </View>
  );
}

/**
 * 발화 고치기·추가 편집기. `sourceCandidates`를 주면(빠진 발화 추가) 이 발화를 근거로 붙일 기억 항목을 고른다.
 * 대화 진행(meta) 발화는 근거가 될 수 없으므로(규칙 15) 그때는 고르지 않는다.
 */
function TurnEditor({
  inline,
  divided,
  title,
  initial,
  sourceCandidates,
  saving,
  onSave,
  onCancel,
}: {
  /** 메시지 목록 카드 안에서 그 자리에 펼칠 때. 카드를 겹치지 않는다. */
  inline?: boolean;
  divided?: boolean;
  title: string;
  initial: { text: string; intent: Intent };
  sourceCandidates?: Item[];
  saving: boolean;
  onSave: (text: string, intent: Intent, sourceOf: number[]) => void;
  onCancel: () => void;
}) {
  const [text, setText] = useState(initial.text);
  const [intent, setIntent] = useState<Intent>(initial.intent);
  const [sourceOf, setSourceOf] = useState<number[]>([]);
  const canBeSource = sourceCandidates && sourceCandidates.length > 0 && intent !== 'meta';

  function toggleSource(id: number) {
    setSourceOf((ids) => (ids.includes(id) ? ids.filter((i) => i !== id) : [...ids, id]));
  }

  const Wrapper = inline ? View : Card;
  return (
    <Wrapper style={[styles.turn, divided && styles.divided]}>
      <ThemedText variant="headline">{title}</ThemedText>
      <TextInput
        accessibilityLabel="메시지 내용"
        value={text}
        onChangeText={setText}
        multiline
        autoFocus
        editable={!saving}
        textAlignVertical="top"
        style={styles.field}
      />
      <ThemedText variant="caption" tone="inkMuted">
        이 메시지는 무엇이었나요?
      </ThemedText>
      <View style={styles.intents}>
        {INTENTS.map((value) => (
          <ChoiceChip key={value} label={intentLabel[value]} selected={value === intent} onPress={() => setIntent(value)} />
        ))}
      </View>
      {canBeSource && (
        <View>
          <ThemedText variant="caption" tone="inkMuted">
            이 메시지가 근거가 되는 내용을 골라 주세요. (선택)
          </ThemedText>
          {sourceCandidates.map((item, i) => (
            <OptionRow
              key={item.id}
              variant="plain"
              multiple
              selected={sourceOf.includes(item.id)}
              onPress={() => toggleSource(item.id)}
              style={i > 0 && styles.divided}>
              <ThemedText variant="caption" tone="inkSecondary">
                {itemKindLabel[item.kind]}
              </ThemedText>
              <ThemedText variant="subhead">{item.content}</ThemedText>
            </OptionRow>
          ))}
        </View>
      )}
      <View style={styles.editorActions}>
        <Button variant="secondary" title="취소" onPress={onCancel} style={styles.editorButton} />
        <Button
          title="저장"
          loading={saving}
          disabled={text.trim().length === 0}
          onPress={() => onSave(text.trim(), intent, canBeSource ? sourceOf : [])}
          style={styles.editorButton}
        />
      </View>
    </Wrapper>
  );
}

const styles = StyleSheet.create({
  step: { gap: spacing.md },
  list: { paddingVertical: spacing.xs },
  turn: { gap: spacing.sm, paddingVertical: spacing.lg },
  turnHeader: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm },
  spacer: { flex: 1 },
  divided: { borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.outline },
  field: {
    ...components.field.typography,
    color: components.field.textColor,
    backgroundColor: components.field.backgroundColor,
    borderRadius: components.field.rounded,
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.md,
    minHeight: components.textAreaCompact.height,
  },
  intents: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
  editorActions: { flexDirection: 'row', gap: spacing.sm },
  editorButton: { flex: 1 },
});
