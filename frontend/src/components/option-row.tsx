import type { ReactNode } from 'react';
import { Pressable, StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';

import { Icon } from '@/components/icon';
import { colors, components, opacity, spacing } from '@/theme';

/**
 * 고를 수 있는 넓은 행. 설명이 붙는 선택지에 쓴다.
 * - `filled`(기본): DESIGN.md answer-option 색의 큰 라운드 상자. 화면에 바로 놓는 선택지.
 * - `plain`: 상자 없이 아이콘과 글자만. 카드 안 목록에서 쓰고, 행 사이는 호출하는 쪽이 구분선으로 나눈다(카드 안 카드 금지).
 * 여러 개 고를 때(`multiple`)는 체크 상자, 하나만 고를 때는 원 아이콘으로 선택 방식을 보여준다.
 */
export function OptionRow({
  selected,
  multiple,
  disabled,
  variant = 'filled',
  onPress,
  style,
  children,
}: {
  selected: boolean;
  multiple?: boolean;
  disabled?: boolean;
  variant?: 'filled' | 'plain';
  onPress: () => void;
  style?: StyleProp<ViewStyle>;
  children: ReactNode;
}) {
  const filled = variant === 'filled';
  return (
    <Pressable
      accessibilityRole={multiple ? 'checkbox' : 'radio'}
      accessibilityState={{ checked: selected, disabled }}
      disabled={disabled}
      onPress={onPress}
      style={({ pressed }) => [
        filled ? styles.filled : styles.plain,
        filled && selected && styles.selected,
        pressed && !(filled && selected) && (filled ? styles.pressed : styles.plainPressed),
        disabled && styles.disabled,
        style,
      ]}>
      <Icon
        name={multiple ? (selected ? 'check-square' : 'square') : selected ? 'check-circle' : 'circle'}
        size="md"
        color={selected ? (filled ? colors.ink : colors.primary) : colors.inkMuted}
      />
      <View style={styles.body}>{children}</View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  filled: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: spacing.md,
    padding: spacing.lg,
    borderRadius: components.feedbackCorrect.rounded,
    backgroundColor: components.answerOption.backgroundColor,
    borderWidth: components.answerOptionOutline.width,
    borderColor: components.answerOptionOutline.backgroundColor,
  },
  plain: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: spacing.md,
    paddingVertical: spacing.md,
  },
  selected: {
    backgroundColor: components.answerOptionSelected.backgroundColor,
    borderColor: components.answerOptionSelected.backgroundColor,
  },
  pressed: { backgroundColor: colors.outline },
  plainPressed: { backgroundColor: components.cardPressed.backgroundColor },
  disabled: { opacity: opacity.disabled },
  body: { flex: 1, gap: spacing.xs },
});
