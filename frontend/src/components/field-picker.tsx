import { useState } from 'react';
import { Pressable, StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';

import type { Schemas } from '@/api/client';
import { useFields } from '@/api/fields';
import { ChoiceChip } from '@/components/choice-chip';
import { Chip } from '@/components/chip';
import { Skeleton } from '@/components/skeleton';
import { ThemedText } from '@/components/themed-text';
import { spacing } from '@/theme';

type FieldLabel = Schemas['FieldLabel'];

/**
 * 세션의 학습 분야 (스펙 §7.10). 지금 라벨을 칩으로 보여주고, "바꾸기"를 누르면 대분류 → 소분류 순으로 고른다.
 * 자동 판정 전이면 "분야를 정하는 중"이다. 세션 확인 단계와 세션 상세에서 쓴다.
 */
export function FieldPicker({
  value,
  busy = false,
  onChoose,
  style,
}: {
  value: FieldLabel | null;
  busy?: boolean;
  onChoose: (code: string) => void;
  style?: StyleProp<ViewStyle>;
}) {
  const [open, setOpen] = useState(false);
  const [field, setField] = useState<string | null>(value?.fieldCode ?? null);
  const fields = useFields(open);
  const options = fields.data?.find((f) => f.code === field)?.subfields ?? [];

  return (
    <View style={[styles.wrap, style]}>
      <View style={styles.row}>
        <ThemedText variant="subhead" tone="inkSecondary">
          분야
        </ThemedText>
        {value ? (
          <Chip label={`${value.fieldLabel} · ${value.label}`} />
        ) : (
          <ThemedText variant="caption" tone="inkMuted">
            분야를 정하는 중이에요
          </ThemedText>
        )}
        <Pressable
          accessibilityRole="button"
          accessibilityState={{ expanded: open, disabled: busy }}
          disabled={busy}
          hitSlop={8}
          onPress={() => {
            setField(value?.fieldCode ?? null);
            setOpen(!open);
          }}
          style={styles.toggle}>
          <ThemedText variant="subhead" tone={busy ? 'inkMuted' : 'primaryInk'}>
            {open ? '닫기' : '바꾸기'}
          </ThemedText>
        </Pressable>
      </View>

      {open && fields.isPending && <Skeleton shape="block" height={spacing['3xl']} />}
      {open && fields.isError && (
        <ThemedText variant="caption" tone="dangerInk">
          분야 목록을 불러오지 못했어요.
        </ThemedText>
      )}
      {open && fields.data && (
        <ThemedText variant="caption" tone="inkMuted">
          큰 분야
        </ThemedText>
      )}
      {open && fields.data && (
        <View accessibilityRole="radiogroup" accessibilityLabel="큰 분야" style={styles.chips}>
          {fields.data.map((f) => (
            <ChoiceChip key={f.code} label={f.label} selected={f.code === field} onPress={() => setField(f.code)} />
          ))}
        </View>
      )}
      {open && options.length > 0 && (
        <ThemedText variant="caption" tone="inkMuted">
          세부 분야
        </ThemedText>
      )}
      {open && options.length > 0 && (
        <View accessibilityRole="radiogroup" accessibilityLabel="세부 분야" style={styles.chips}>
          {options.map((s) => (
            <ChoiceChip
              key={s.code}
              label={s.label}
              selected={s.code === value?.code}
              disabled={busy}
              onPress={() => {
                onChoose(s.code);
                setOpen(false);
              }}
            />
          ))}
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { gap: spacing.sm },
  row: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm, flexWrap: 'wrap' },
  toggle: { marginLeft: 'auto' },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
});
