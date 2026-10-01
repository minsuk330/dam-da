import { StyleSheet, TextInput, View } from 'react-native';

import type { Presentation } from '@/api/practice';
import { ThemedText } from '@/components/themed-text';
import { colors, components, spacing } from '@/theme';

import { AnswerOption, type OptionState } from './answer-option';

/** 객관식은 고른 번호(0부터), 그 밖의 유형은 글로 쓴 답. */
export type Answer = { choiceIndex: number | null; text: string };

export const emptyAnswer: Answer = { choiceIndex: null, text: '' };

export function isAnswered(type: Presentation['type'], answer: Answer): boolean {
  return type === 'MULTIPLE_CHOICE' ? answer.choiceIndex !== null : answer.text.trim().length > 0;
}

const placeholder: Record<Exclude<Presentation['type'], 'MULTIPLE_CHOICE'>, string> = {
  SHORT_ANSWER: '짧게 답해 주세요.',
  ESSAY: '떠오르는 대로 내 말로 써 보세요.',
  ERROR_FINDING: '어디가 틀렸는지, 바르게 고치면 무엇인지 써 주세요.',
  CASE_JUDGMENT: '이 상황에서 어떻게 되는지와 그 이유를 써 주세요.',
  CASE_APPLICATION: '배운 내용을 이 상황에 적용해 답해 주세요.',
};

/** 선택지가 이보다 길면 한 줄에 하나씩 놓는다. */
const WIDE_CHOICE_LENGTH = 14;

/**
 * 문제 유형별 답 입력. `result`가 있으면 판정 결과를 보여주고 입력을 막는다.
 * 객관식 정답 번호는 서버가 주지 않으므로 고른 선택지만 맞음·틀림으로 표시한다.
 */
export function AnswerInput({
  presentation,
  answer,
  onChange,
  locked,
  result,
}: {
  presentation: Presentation;
  answer: Answer;
  onChange: (answer: Answer) => void;
  locked: boolean;
  result: 'correct' | 'wrong' | null;
}) {
  const { type, choices } = presentation;

  if (type === 'MULTIPLE_CHOICE') {
    const wide = choices.some((c) => c.length > WIDE_CHOICE_LENGTH);
    const state = (i: number): OptionState => {
      if (!locked) return answer.choiceIndex === i ? 'selected' : 'idle';
      if (i !== answer.choiceIndex) return 'dimmed';
      return result ?? 'selected';
    };
    return (
      <View accessibilityRole="radiogroup" style={styles.grid}>
        {choices.map((choice, i) => (
          <AnswerOption
            key={`${i}-${choice}`}
            label={choice}
            wide={wide}
            state={state(i)}
            onPress={() => onChange({ ...answer, choiceIndex: i })}
          />
        ))}
      </View>
    );
  }

  const multiline = type !== 'SHORT_ANSWER';
  return (
    <View style={styles.textAnswer}>
      <TextInput
        accessibilityLabel="답 입력"
        value={answer.text}
        onChangeText={(text) => onChange({ ...answer, text })}
        placeholder={placeholder[type]}
        placeholderTextColor={components.fieldPlaceholder.textColor}
        multiline={multiline}
        textAlignVertical={multiline ? 'top' : 'center'}
        returnKeyType={multiline ? 'default' : 'done'}
        editable={!locked}
        style={[styles.field, multiline && styles.fieldEssay, locked && styles.fieldLocked]}
      />
      {type === 'ESSAY' && !locked && (
        <ThemedText variant="caption" tone="inkMuted">
          정답 문장을 외울 필요 없어요. 핵심이 들어 있으면 맞아요.
        </ThemedText>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.md },
  textAnswer: { gap: spacing.sm },
  field: {
    ...components.field.typography,
    color: components.field.textColor,
    backgroundColor: components.field.backgroundColor,
    borderRadius: components.field.rounded,
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.md,
  },
  fieldEssay: { minHeight: components.textArea.height },
  fieldLocked: { color: colors.inkSecondary },
});
