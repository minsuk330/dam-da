import { router } from 'expo-router';
import { useState } from 'react';
import { Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { IntakeError, useSubmitConversation, type ConversationInput } from '@/api/conversation-input';
import { Button } from '@/components/button';
import { Icon } from '@/components/icon';
import { useTabBarSpace } from '@/components/tab-bar';
import { ThemedText } from '@/components/themed-text';
import { colors, components, spacing } from '@/theme';

type Mode = ConversationInput['kind'];

const MODES: { mode: Mode; label: string }[] = [
  { mode: 'share_link', label: '공유 링크' },
  { mode: 'paste', label: '붙여넣기' },
];

/** 추가 탭: Claude 공유 링크나 복사한 대화를 넣어 학습 대화로 저장한다 (스펙 §7.6 입력 경로). */
export function AddConversation() {
  const tabBarSpace = useTabBarSpace();
  const insets = useSafeAreaInsets();
  const [mode, setMode] = useState<Mode>('share_link');
  const [url, setUrl] = useState('');
  const [text, setText] = useState('');
  const submit = useSubmitConversation();

  const value = mode === 'share_link' ? url.trim() : text.trim();
  const error = submit.error instanceof IntakeError ? submit.error : null;

  function changeMode(next: Mode) {
    setMode(next);
    submit.reset();
  }

  function send() {
    submit.mutate(mode === 'share_link' ? { kind: 'share_link', url: value } : { kind: 'paste', text: value });
  }

  if (submit.isSuccess) {
    const saved = submit.data;
    return (
      <View style={[styles.center, { paddingBottom: tabBarSpace }]}>
        <View style={styles.doneIcon}>
          <Icon name="check" size="lg" color={colors.successInk} />
        </View>
        <ThemedText variant="title">대화를 저장했어요</ThemedText>
        <ThemedText variant="subhead" tone="inkSecondary" style={styles.centerText}>
          발화 {saved.userTurnCount}개 · 복습 단위 {saved.reviewUnitCount}개
          {saved.warnings.length > 0 ? ` · 확인할 점 ${saved.warnings.length}개` : ''}
        </ThemedText>
        <ThemedText variant="caption" tone="inkMuted" style={styles.centerText}>
          검수가 끝나면 뽑은 내용을 확인하고 첫 학습을 준비해요.
        </ThemedText>
        <View style={styles.actions}>
          {saved.learningSessionId !== null ? (
            <Button
              title="학습 내용 확인하기"
              onPress={() =>
                router.push({ pathname: '/sessions/[id]', params: { id: String(saved.learningSessionId) } })
              }
            />
          ) : (
            <Button title="기억 탭에서 보기" onPress={() => router.navigate('/memory')} />
          )}
          <Button
            variant="secondary"
            title="다른 대화 추가"
            onPress={() => {
              setUrl('');
              setText('');
              submit.reset();
            }}
          />
        </View>
      </View>
    );
  }

  return (
    <ScrollView
      keyboardShouldPersistTaps="handled"
      contentInsetAdjustmentBehavior="automatic"
      contentContainerStyle={[styles.content, { paddingTop: insets.top + spacing.lg, paddingBottom: tabBarSpace }]}>
      <View style={styles.intro}>
        <ThemedText variant="display">AI와 나눈 대화를{'\n'}추가해요</ThemedText>
        <ThemedText variant="subhead" tone="inkMuted">
          대화에서 배운 내용을 뽑아 복습 문제로 만들어요.
        </ThemedText>
      </View>

      <View accessibilityRole="tablist" style={styles.modes}>
        {MODES.map((m) => {
          const selected = m.mode === mode;
          return (
            <Pressable
              key={m.mode}
              accessibilityRole="tab"
              accessibilityState={{ selected }}
              onPress={() => changeMode(m.mode)}
              style={[styles.mode, selected && styles.modeSelected]}>
              <ThemedText variant="headline">{m.label}</ThemedText>
            </Pressable>
          );
        })}
      </View>

      {mode === 'share_link' ? (
        <View style={styles.fieldGroup}>
          <TextInput
            accessibilityLabel="공유 링크"
            value={url}
            onChangeText={setUrl}
            placeholder="https://claude.ai/share/..."
            placeholderTextColor={components.fieldPlaceholder.textColor}
            autoCapitalize="none"
            autoCorrect={false}
            keyboardType="url"
            inputMode="url"
            editable={!submit.isPending}
            style={styles.field}
          />
          <ThemedText variant="caption" tone="inkMuted">
            Claude 대화 화면의 공유 버튼으로 만든 링크를 붙여넣어 주세요.
          </ThemedText>
        </View>
      ) : (
        <View style={styles.fieldGroup}>
          <TextInput
            accessibilityLabel="대화 내용"
            value={text}
            onChangeText={setText}
            placeholder="대화 전체를 복사해 붙여넣어 주세요."
            placeholderTextColor={components.fieldPlaceholder.textColor}
            multiline
            textAlignVertical="top"
            editable={!submit.isPending}
            style={[styles.field, styles.fieldMultiline]}
          />
          <ThemedText variant="caption" tone="inkMuted">
            붙여넣은 대화는 모델이 옮겨 적은 것으로 표시돼요. 저장 뒤 원문과 맞는지 확인할 수 있어요.
          </ThemedText>
        </View>
      )}

      {error && (
        <View accessibilityRole="alert" style={styles.error}>
          <ThemedText variant="subhead" tone="dangerInk">
            {error.message}
          </ThemedText>
          {error.fallback === 'paste' && mode === 'share_link' && (
            <Button variant="secondary" title="붙여넣기로 바꾸기" onPress={() => changeMode('paste')} />
          )}
        </View>
      )}
      {submit.isError && !error && (
        <View accessibilityRole="alert" style={styles.error}>
          <ThemedText variant="subhead" tone="dangerInk">
            서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.
          </ThemedText>
        </View>
      )}

      <View style={styles.submit}>
        <Button title="학습 내용 만들기" disabled={value.length === 0} loading={submit.isPending} onPress={send} />
        {submit.isPending && (
          <ThemedText variant="caption" tone="inkMuted" style={styles.centerText}>
            대화에서 학습 내용을 뽑는 중이에요. 30초쯤 걸릴 수 있어요.
          </ThemedText>
        )}
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  content: { padding: spacing.xl, gap: spacing['2xl'] },
  intro: { gap: spacing.sm },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: spacing.md, padding: spacing.xl },
  centerText: { textAlign: 'center' },
  doneIcon: {
    width: components.iconButton.size,
    height: components.iconButton.size,
    borderRadius: components.iconButton.rounded,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: colors.successTint,
  },
  actions: { alignSelf: 'stretch', gap: spacing.sm, marginTop: spacing.lg },
  modes: { flexDirection: 'row', gap: spacing.sm },
  mode: {
    flex: 1,
    height: components.answerOption.height,
    borderRadius: components.answerOption.rounded,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: components.answerOption.backgroundColor,
    borderWidth: components.answerOptionOutline.width,
    borderColor: components.answerOptionOutline.backgroundColor,
  },
  modeSelected: {
    backgroundColor: components.answerOptionSelected.backgroundColor,
    borderColor: components.answerOptionSelected.backgroundColor,
  },
  fieldGroup: { gap: spacing.sm },
  field: {
    ...components.field.typography,
    color: components.field.textColor,
    backgroundColor: components.field.backgroundColor,
    borderRadius: components.field.rounded,
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.md,
  },
  fieldMultiline: { minHeight: components.textAreaLarge.height },
  error: {
    gap: spacing.md,
    padding: components.feedbackWrong.padding,
    borderRadius: components.feedbackWrong.rounded,
    backgroundColor: components.feedbackWrong.backgroundColor,
  },
  submit: { gap: spacing.sm },
});
