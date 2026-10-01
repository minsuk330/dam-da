import { Pressable, StyleSheet, Text, View } from 'react-native';

import { Icon } from '@/components/icon';
import { colors, components, spacing } from '@/theme';

export type OptionState = 'idle' | 'selected' | 'correct' | 'wrong' | 'dimmed';

/** DESIGN.md의 answer-option 계열. 채점 후에는 정답·오답을 색 + 아이콘으로 함께 보여준다. */
export function AnswerOption({
  label,
  state,
  onPress,
}: {
  label: string;
  state: OptionState;
  onPress?: () => void;
}) {
  const graded = state === 'correct' || state === 'wrong' || state === 'dimmed';
  const look = {
    idle: { bg: components.answerOption.backgroundColor, text: components.answerOption.textColor, border: colors.outline },
    selected: { bg: components.answerOptionSelected.backgroundColor, text: components.answerOptionSelected.textColor, border: components.answerOptionSelected.backgroundColor },
    correct: { bg: components.answerOptionCorrect.backgroundColor, text: components.answerOptionCorrect.textColor, border: components.stateRingCorrect.backgroundColor },
    wrong: { bg: components.answerOptionWrong.backgroundColor, text: components.answerOptionWrong.textColor, border: components.stateRingWrong.backgroundColor },
    dimmed: { bg: components.answerOption.backgroundColor, text: colors.inkMuted, border: colors.outline },
  }[state];
  const borderWidth =
    state === 'correct' || state === 'wrong' ? components.stateRingCorrect.width : components.answerOptionOutline.width;

  return (
    <Pressable
      accessibilityRole="radio"
      accessibilityState={{ selected: state === 'selected', disabled: graded }}
      accessibilityLabel={`${label}${state === 'correct' ? ', 정답' : state === 'wrong' ? ', 오답' : ''}`}
      disabled={graded}
      onPress={onPress}
      style={({ pressed }) => [
        styles.option,
        { backgroundColor: look.bg, borderColor: look.border, borderWidth },
        pressed && styles.pressed,
      ]}>
      <View style={styles.content}>
        {state === 'correct' && <Icon name="check" size="md" color={look.text} />}
        {state === 'wrong' && <Icon name="x" size="md" color={look.text} />}
        <Text style={[components.answerOption.typography, { color: look.text }]} numberOfLines={2}>
          {label}
        </Text>
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  option: {
    flexBasis: '47%',
    flexGrow: 1,
    minHeight: components.answerOption.height,
    borderRadius: components.answerOption.rounded,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.sm,
  },
  content: { flexDirection: 'row', alignItems: 'center', gap: spacing.xs },
  pressed: { backgroundColor: colors.outline },
});
