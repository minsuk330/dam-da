import { useState } from 'react';
import { Pressable, StyleSheet, TextInput, View } from 'react-native';

import type { Schemas } from '@/api/client';
import { useEditLearningSession } from '@/api/learning-sessions';
import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { Chip } from '@/components/chip';
import { ThemedText } from '@/components/themed-text';
import { aiVerdictLabel, intentLabel } from '@/labels';
import { colors, components, spacing } from '@/theme';

import { Notice, StepHeader } from './parts';

type Detail = Schemas['LearningSessionDetail'];
type Turn = Schemas['Turn'];
type Intent = Turn['intent'];

const INTENTS = Object.keys(intentLabel) as Intent[];

/** 1단계: 대화를 맞게 옮겼는지 확인한다. 발화를 고치거나, 대화 진행(meta)으로 바꾸거나, 빠진 발화를 넣는다. */
export function TurnsStep({ session, onNext }: { session: Detail; onNext: () => void }) {
  const edit = useEditLearningSession(session.id);
  const [editing, setEditing] = useState<number | 'new' | null>(null);
  const lastIndex = session.turns.at(-1)?.index ?? 0;

  return (
    <View style={styles.step}>
      <StepHeader
        step={1}
        total={3}
        title="대화를 맞게 옮겼는지 확인해요"
        description="내가 한 말만 보여드려요. 다르게 옮겨졌으면 고쳐 주세요."
      />

      {session.fidelity === 'model_transcribed' && (
        <Notice>모델이 옮겨 적은 대화예요. 확인하기 전에는 원문으로 취급하지 않아요.</Notice>
      )}
      {edit.error && <Notice tone="danger">{edit.error.message}</Notice>}

      {session.turns.map((turn) =>
        editing === turn.index ? (
          <TurnEditor
            key={turn.index}
            title={`발화 ${turn.index} 고치기`}
            initial={turn}
            saving={edit.isPending}
            onCancel={() => setEditing(null)}
            onSave={(text, intent) =>
              edit.mutate(
                { kind: 'turn', index: turn.index, content: { text, intent, aiVerdict: turn.aiVerdict, correction: turn.correction } },
                { onSuccess: () => setEditing(null) },
              )
            }
          />
        ) : (
          <TurnCard key={turn.index} turn={turn} onEdit={() => setEditing(turn.index)} disabled={editing !== null} />
        ),
      )}

      {editing === 'new' ? (
        <TurnEditor
          title="빠진 발화 추가"
          initial={{ text: '', intent: 'info_request' }}
          saving={edit.isPending}
          onCancel={() => setEditing(null)}
          onSave={(text, intent) =>
            edit.mutate(
              { kind: 'insert', afterIndex: lastIndex, content: { text, intent }, sourceOf: [] },
              { onSuccess: () => setEditing(null) },
            )
          }
        />
      ) : (
        <Button variant="secondary" title="빠진 발화 추가" disabled={editing !== null} onPress={() => setEditing('new')} />
      )}

      <Button title="다음: 복습할 내용 확인" disabled={editing !== null} onPress={onNext} />
    </View>
  );
}

function TurnCard({ turn, onEdit, disabled }: { turn: Turn; onEdit: () => void; disabled: boolean }) {
  const meta = turn.intent === 'meta';
  const verdict = turn.aiVerdict ? aiVerdictLabel[turn.aiVerdict] : null;
  return (
    <Card style={[styles.turn, meta && styles.turnMeta]}>
      <View style={styles.turnHeader}>
        <ThemedText variant="caption" tone="inkMuted">
          발화 {turn.index}
        </ThemedText>
        <Chip label={intentLabel[turn.intent]} />
        <View style={styles.spacer} />
        <Pressable accessibilityRole="button" accessibilityLabel={`발화 ${turn.index} 고치기`} disabled={disabled} onPress={onEdit} hitSlop={8}>
          <ThemedText variant="subhead" tone={disabled ? 'inkMuted' : 'primaryInk'}>
            고치기
          </ThemedText>
        </Pressable>
      </View>
      <ThemedText tone={meta ? 'inkMuted' : 'ink'}>{turn.text}</ThemedText>
      {meta && (
        <ThemedText variant="caption" tone="inkMuted">
          대화 진행 발화는 복습 근거로 쓰지 않아요.
        </ThemedText>
      )}
      {verdict && (
        <ThemedText variant="caption" tone="inkSecondary">
          대화 중 AI 판정: {verdict}
          {turn.correction ? ` · ${turn.correction}` : ''}
        </ThemedText>
      )}
    </Card>
  );
}

function TurnEditor({
  title,
  initial,
  saving,
  onSave,
  onCancel,
}: {
  title: string;
  initial: { text: string; intent: Intent };
  saving: boolean;
  onSave: (text: string, intent: Intent) => void;
  onCancel: () => void;
}) {
  const [text, setText] = useState(initial.text);
  const [intent, setIntent] = useState<Intent>(initial.intent);
  return (
    <Card style={styles.turn}>
      <ThemedText variant="headline">{title}</ThemedText>
      <TextInput
        accessibilityLabel="발화 내용"
        value={text}
        onChangeText={setText}
        multiline
        autoFocus
        editable={!saving}
        textAlignVertical="top"
        style={styles.field}
      />
      <ThemedText variant="caption" tone="inkMuted">
        이 발화는 무엇이었나요?
      </ThemedText>
      <View style={styles.intents}>
        {INTENTS.map((value) => {
          const selected = value === intent;
          return (
            <Pressable
              key={value}
              accessibilityRole="radio"
              accessibilityState={{ checked: selected }}
              onPress={() => setIntent(value)}
              style={[styles.intent, selected && styles.intentSelected]}>
              <ThemedText variant="caption" tone={selected ? 'onPrimary' : 'ink'}>
                {intentLabel[value]}
              </ThemedText>
            </Pressable>
          );
        })}
      </View>
      <View style={styles.editorActions}>
        <Button variant="secondary" title="취소" onPress={onCancel} style={styles.editorButton} />
        <Button
          title="저장"
          loading={saving}
          disabled={text.trim().length === 0}
          onPress={() => onSave(text.trim(), intent)}
          style={styles.editorButton}
        />
      </View>
    </Card>
  );
}

const styles = StyleSheet.create({
  step: { gap: spacing.md },
  turn: { gap: spacing.sm },
  turnMeta: { backgroundColor: colors.surfaceSoft },
  turnHeader: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm },
  spacer: { flex: 1 },
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
  intent: {
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    borderRadius: components.chipSoft.rounded,
    backgroundColor: colors.surfaceSoft,
    borderWidth: components.answerOptionOutline.width,
    borderColor: components.answerOptionOutline.backgroundColor,
  },
  intentSelected: { backgroundColor: colors.primary, borderColor: colors.primary },
  editorActions: { flexDirection: 'row', gap: spacing.sm },
  editorButton: { flex: 1 },
});
