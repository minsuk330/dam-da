import { Pressable, StyleSheet, TextInput, View } from 'react-native';

import { Icon } from '@/components/icon';
import { ThemedText } from '@/components/themed-text';
import { colors, components, spacing } from '@/theme';

import { AnswerOption, type OptionState } from './answer-option';
import type { Answer, Question } from './sample-questions';

/**
 * 문제 유형별 답 입력. `graded`면 채점 결과를 보여주고 입력을 막는다.
 * `revealAnswer`가 false면 오답만 표시하고 정답은 숨긴다(힌트 후 재도전 전).
 */
export function AnswerInput({
  question,
  answer,
  onChange,
  graded,
  revealAnswer,
}: {
  question: Question;
  answer: Answer;
  onChange: (answer: Answer) => void;
  graded: boolean;
  revealAnswer: boolean;
}) {
  if (question.type === 'MULTIPLE_CHOICE' && answer.type === 'MULTIPLE_CHOICE') {
    const state = (i: number): OptionState => {
      if (!graded) return answer.index === i ? 'selected' : 'idle';
      if (revealAnswer && i === question.answerIndex) return 'correct';
      if (i === answer.index) return i === question.answerIndex ? 'correct' : 'wrong';
      return 'dimmed';
    };
    return (
      <View accessibilityRole="radiogroup" style={styles.grid}>
        {question.choices.map((choice, i) => (
          <AnswerOption key={choice} label={choice} state={state(i)} onPress={() => onChange({ ...answer, index: i })} />
        ))}
      </View>
    );
  }

  if (question.type === 'ERROR_FINDING' && answer.type === 'ERROR_FINDING') {
    return (
      <View style={styles.errorFinding}>
        <View accessibilityRole="radiogroup" accessibilityLabel="틀린 부분 고르기" style={styles.segments}>
          {question.segments.map((segment, i) => {
            const selected = answer.index === i;
            const correct = graded && (revealAnswer || selected) && i === question.wrongIndex;
            const wrong = graded && selected && i !== question.wrongIndex;
            return (
              <Pressable
                key={segment}
                accessibilityRole="radio"
                accessibilityState={{ checked: selected, disabled: graded }}
                accessibilityLabel={`${segment}${correct ? ', 틀린 부분' : ''}`}
                disabled={graded}
                onPress={() => onChange({ ...answer, index: i })}
                style={[
                  styles.segment,
                  selected && !graded && styles.segmentSelected,
                  correct && styles.segmentCorrect,
                  wrong && styles.segmentWrong,
                ]}>
                {correct && <Icon name="check" size="sm" color={colors.successInk} />}
                {wrong && <Icon name="x" size="sm" color={colors.dangerInk} />}
                <ThemedText variant="headline" tone={correct ? 'successInk' : wrong ? 'dangerInk' : 'ink'}>
                  {segment}
                </ThemedText>
              </Pressable>
            );
          })}
        </View>
        <TextInput
          accessibilityLabel="바르게 고쳐 쓰기"
          value={answer.correction}
          onChangeText={(correction) => onChange({ ...answer, correction })}
          placeholder="바르게 고쳐 쓰기 (선택)"
          placeholderTextColor={components.fieldPlaceholder.textColor}
          editable={!graded}
          style={[styles.field, graded && styles.fieldGraded]}
        />
      </View>
    );
  }

  if (answer.type === 'SHORT_ANSWER' || answer.type === 'ESSAY') {
    const essay = answer.type === 'ESSAY';
    return (
      <View style={styles.textAnswer}>
        <TextInput
          accessibilityLabel="답 입력"
          value={answer.text}
          onChangeText={(text) => onChange({ ...answer, text })}
          placeholder={essay ? '떠오르는 대로 내 말로 써 보세요.' : '짧게 답해 주세요.'}
          placeholderTextColor={components.fieldPlaceholder.textColor}
          multiline={essay}
          textAlignVertical={essay ? 'top' : 'center'}
          returnKeyType={essay ? 'default' : 'done'}
          editable={!graded}
          style={[styles.field, essay && styles.fieldEssay, graded && styles.fieldGraded]}
        />
        {essay && !graded && (
          <ThemedText variant="caption" tone="inkMuted">
            정답 문장을 외울 필요 없어요. 핵심이 들어 있으면 맞아요.
          </ThemedText>
        )}
      </View>
    );
  }

  return null;
}

const styles = StyleSheet.create({
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.md },
  errorFinding: { gap: spacing.lg },
  segments: { gap: spacing.sm },
  segment: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.xs,
    minHeight: components.answerOption.height,
    paddingHorizontal: spacing.lg,
    borderRadius: components.answerOption.rounded,
    backgroundColor: components.answerOption.backgroundColor,
    borderWidth: components.answerOptionOutline.width,
    borderColor: components.answerOptionOutline.backgroundColor,
  },
  segmentSelected: {
    backgroundColor: components.answerOptionSelected.backgroundColor,
    borderColor: components.answerOptionSelected.backgroundColor,
  },
  segmentCorrect: {
    backgroundColor: components.answerOptionCorrect.backgroundColor,
    borderColor: components.stateRingCorrect.backgroundColor,
    borderWidth: components.stateRingCorrect.width,
  },
  segmentWrong: {
    backgroundColor: components.answerOptionWrong.backgroundColor,
    borderColor: components.stateRingWrong.backgroundColor,
    borderWidth: components.stateRingWrong.width,
  },
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
  fieldGraded: { color: colors.inkSecondary },
});
