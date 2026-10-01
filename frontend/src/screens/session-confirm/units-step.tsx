import { Pressable, StyleSheet, View } from 'react-native';

import type { Schemas } from '@/api/client';
import { useEditLearningSession } from '@/api/learning-sessions';
import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { Notice } from '@/components/notice';
import { Chip } from '@/components/chip';
import { OptionRow } from '@/components/option-row';
import { ThemedText } from '@/components/themed-text';
import { itemKindLabel, unitVerdictLabel, unitVerdictNote } from '@/labels';
import { colors, opacity, spacing } from '@/theme';

import { StepHeader } from './parts';

type Detail = Schemas['LearningSessionDetail'];
type Unit = Schemas['Unit'];

/**
 * 2단계: 복습할 내용을 고른다. Jev 검수 결과는 판정만 보여주고, 넣고 뺄지는 사용자가 정한다.
 * 확인을 마치면 내용을 고칠 수 없고 문제 생성이 열린다(규칙 12).
 */
export function UnitsStep({ session, onBack, onConfirmed }: { session: Detail; onBack: () => void; onConfirmed: () => void }) {
  const edit = useEditLearningSession(session.id);
  const items = session.units.flatMap((u) => u.items);
  const included = items.filter((i) => i.status !== 'EXCLUDED').length;

  return (
    <View style={styles.step}>
      <StepHeader
        step={2}
        total={3}
        title="복습할 내용을 골라요"
        description="대화에서 뽑은 내용이에요. 복습하고 싶지 않은 것은 빼 주세요."
      />

      {session.warnings.map((warning) => (
        <Notice key={warning}>{warning}</Notice>
      ))}
      {edit.error && <Notice tone="danger">{edit.error.message}</Notice>}

      {session.units.map((unit) => (
        <UnitCard
          key={unit.id}
          unit={unit}
          busy={edit.isPending}
          onToggleUnit={() => edit.mutate({ kind: 'unit', unitId: unit.id, excluded: !unit.excluded })}
          onToggleItem={(itemId, excluded) => edit.mutate({ kind: 'item', itemId, excluded })}
        />
      ))}

      <ThemedText variant="subhead" tone="inkSecondary" style={styles.count}>
        기억할 내용 {items.length}개 중 {included}개를 복습해요
      </ThemedText>
      <Button
        title="확인 완료"
        disabled={included === 0 || edit.isPending}
        loading={edit.isPending && edit.variables?.kind === 'confirm'}
        onPress={() => edit.mutate({ kind: 'confirm' }, { onSuccess: onConfirmed })}
      />
      <Button variant="secondary" title="이전: 메시지 확인" disabled={edit.isPending} onPress={onBack} />
    </View>
  );
}

function UnitCard({
  unit,
  busy,
  onToggleUnit,
  onToggleItem,
}: {
  unit: Unit;
  busy: boolean;
  onToggleUnit: () => void;
  onToggleItem: (itemId: number, excluded: boolean) => void;
}) {
  return (
    <Card style={[styles.unit, unit.excluded && styles.unitExcluded]}>
      <View style={styles.unitHeader}>
        <ThemedText variant="headline" tone={unit.excluded ? 'inkMuted' : 'ink'} style={styles.unitTitle}>
          {unit.title}
        </ThemedText>
        <Chip variant={unit.verdict === 'APPROVED' ? 'soft' : 'warning'} label={unitVerdictLabel[unit.verdict]} />
      </View>
      {unitVerdictNote[unit.verdict] && (
        <ThemedText variant="caption" tone="inkSecondary">
          {unitVerdictNote[unit.verdict]}
        </ThemedText>
      )}
      <ThemedText variant="caption" tone="inkMuted">
        {unit.evidenceTurns.length > 0 ? `근거 메시지 ${unit.evidenceTurns.join(', ')}` : '근거 메시지 없음'}
      </ThemedText>

      {unit.items.map((item, i) => {
        const included = item.status !== 'EXCLUDED';
        return (
          <OptionRow
            key={item.id}
            variant="plain"
            style={i > 0 && styles.divided}
            multiple
            selected={included}
            disabled={busy || unit.excluded}
            onPress={() => onToggleItem(item.id, included)}>
            <ThemedText variant="caption" tone="inkSecondary">
              {itemKindLabel[item.kind]} · 메시지 {item.sourceTurns.join(', ') || '없음'}
            </ThemedText>
            <ThemedText variant="subhead" tone={included ? 'ink' : 'inkMuted'}>
              {item.content}
            </ThemedText>
          </OptionRow>
        );
      })}

      <Pressable accessibilityRole="button" disabled={busy} onPress={onToggleUnit} hitSlop={8} style={styles.unitToggle}>
        <ThemedText variant="subhead" tone={busy ? 'inkMuted' : 'primaryInk'}>
          {unit.excluded ? '이 주제 다시 넣기' : '이 주제 빼기'}
        </ThemedText>
      </Pressable>
    </Card>
  );
}

const styles = StyleSheet.create({
  step: { gap: spacing.md },
  unit: { gap: spacing.sm },
  unitExcluded: { opacity: opacity.excluded },
  unitHeader: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm },
  unitTitle: { flex: 1 },
  unitToggle: { alignSelf: 'flex-end', paddingTop: spacing.xs },
  count: { textAlign: 'center', marginTop: spacing.sm },
  divided: { borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.outline },
});
