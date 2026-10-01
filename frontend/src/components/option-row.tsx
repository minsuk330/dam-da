import type { ReactNode } from 'react';
import { Pressable, StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';

import { Icon } from '@/components/icon';
import { colors, components, spacing } from '@/theme';

/**
 * 고를 수 있는 넓은 행(DESIGN.md answer-option 색, 큰 라운드). 설명이 붙는 선택지에 쓴다.
 * 여러 개 고를 때(`multiple`)는 체크 상자, 하나만 고를 때는 원 아이콘으로 선택 방식을 보여준다.
 */
export function OptionRow({
  selected,
  multiple,
  disabled,
  onPress,
  style,
  children,
}: {
  selected: boolean;
  multiple?: boolean;
  disabled?: boolean;
  onPress: () => void;
  style?: StyleProp<ViewStyle>;
  children: ReactNode;
}) {
  return (
    <Pressable
      accessibilityRole={multiple ? 'checkbox' : 'radio'}
      accessibilityState={{ checked: selected, disabled }}
      disabled={disabled}
      onPress={onPress}
      style={({ pressed }) => [
        styles.option,
        selected && styles.selected,
        pressed && !selected && styles.pressed,
        disabled && styles.disabled,
        style,
      ]}>
      <Icon
        name={multiple ? (selected ? 'check-square' : 'square') : selected ? 'check-circle' : 'circle'}
        size={20}
        color={selected ? colors.ink : colors.inkMuted}
      />
      <View style={styles.body}>{children}</View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  option: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: spacing.md,
    padding: spacing.lg,
    borderRadius: components.feedbackCorrect.rounded,
    backgroundColor: components.answerOption.backgroundColor,
    borderWidth: components.answerOptionOutline.width,
    borderColor: components.answerOptionOutline.backgroundColor,
  },
  selected: {
    backgroundColor: components.answerOptionSelected.backgroundColor,
    borderColor: components.answerOptionSelected.backgroundColor,
  },
  pressed: { backgroundColor: colors.outline },
  disabled: { opacity: 0.5 },
  body: { flex: 1, gap: spacing.xs },
});
