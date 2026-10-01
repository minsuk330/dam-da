import { Pressable, StyleSheet, type StyleProp, type ViewStyle } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { colors, components, opacity, spacing } from '@/theme';

/**
 * 하나만 고르는 알약형 선택지(라디오). DESIGN.md answer-option 색을 쓰고, 고른 것은 answer-option-selected로 칠한다.
 * 짧은 값 여러 개를 한 줄에 늘어놓을 때 쓴다(학습 시간·알림 시각·확신도). 설명이 붙는 선택지는 `OptionRow`.
 */
export function ChoiceChip({
  label,
  selected,
  disabled,
  onPress,
  accessibilityLabel,
  style,
}: {
  label: string;
  selected: boolean;
  disabled?: boolean;
  onPress: () => void;
  /** 칩 글자가 줄임말이면 스크린 리더용 전체 문장. */
  accessibilityLabel?: string;
  style?: StyleProp<ViewStyle>;
}) {
  return (
    <Pressable
      accessibilityRole="radio"
      accessibilityLabel={accessibilityLabel ?? label}
      accessibilityState={{ checked: selected, disabled }}
      disabled={disabled}
      onPress={onPress}
      style={({ pressed }) => [
        styles.chip,
        selected && styles.selected,
        pressed && !selected && styles.pressed,
        disabled && styles.disabled,
        style,
      ]}>
      <ThemedText variant="subhead">{label}</ThemedText>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  chip: {
    alignItems: 'center',
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    borderRadius: components.answerOption.rounded,
    backgroundColor: components.answerOption.backgroundColor,
    borderWidth: components.answerOptionOutline.width,
    borderColor: components.answerOptionOutline.backgroundColor,
  },
  selected: {
    backgroundColor: components.answerOptionSelected.backgroundColor,
    borderColor: components.answerOptionSelected.backgroundColor,
  },
  pressed: { backgroundColor: colors.outline },
  disabled: { opacity: opacity.disabled },
});
