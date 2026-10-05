import { router } from 'expo-router';
import { useState } from 'react';
import { Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { IntakeError, useSubmitConversation, type ConversationInput } from '@/api/conversation-input';
import { useSession } from '@/api/session';
import { AI_NOTICE } from '@/screens/intro-steps';
import { guessShareSource } from '@/screens/share-guide/steps';
import { Button } from '@/components/button';
import { Icon } from '@/components/icon';
import { useTabBarSpace } from '@/components/tab-bar';
import { ThemedText } from '@/components/themed-text';
import { colors, components, spacing } from '@/theme';
import { inToss } from '@/toss';

type Mode = ConversationInput['kind'];

/** 대화를 처음 저장할 때 동의받는 문서(#149). 로그인 전후 모두 열리는 공개 페이지다. */
const AGREEMENT_LINKS = [
  { label: '이용약관', href: '/terms' },
  { label: '개인정보 수집·이용', href: '/consent/privacy' },
  { label: '개인정보 국외 이전', href: '/consent/overseas' },
] as const;

const MODES: { mode: Mode; label: string }[] = [
  { mode: 'share_link', label: '공유 링크' },
  { mode: 'paste', label: '붙여넣기' },
];

/** 추가 탭: ChatGPT·Claude·Codex 공유 링크나 복사한 대화를 넣어 학습 대화로 저장한다 (스펙 §7.5 입력 경로). */
export function AddConversation() {
  const tabBarSpace = useTabBarSpace();
  const insets = useSafeAreaInsets();
  const [mode, setMode] = useState<Mode>('share_link');
  const [url, setUrl] = useState('');
  const [text, setText] = useState('');
  const submit = useSubmitConversation();
  const { user, agree } = useSession();
  // 토스 익명 계정은 대화를 처음 저장할 때 약관에 동의한다(#149). 저장 버튼이 동의까지 함께 한다.
  const needsAgreement = user?.agreementRequired === true;
  const [agreeing, setAgreeing] = useState(false);
  const [agreeError, setAgreeError] = useState(false);

  const value = mode === 'share_link' ? url.trim() : text.trim();
  const error = submit.error instanceof IntakeError ? submit.error : null;

  function changeMode(next: Mode) {
    setMode(next);
    submit.reset();
  }

  async function send() {
    if (needsAgreement) {
      setAgreeing(true);
      setAgreeError(false);
      try {
        await agree();
      } catch {
        setAgreeError(true);
        return;
      } finally {
        setAgreeing(false);
      }
    }
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
          메시지 {saved.userTurnCount}개 · 주제 {saved.reviewUnitCount}개
          {saved.warnings.length > 0 ? ` · 확인할 점 ${saved.warnings.length}개` : ''}
        </ThemedText>
        <ThemedText variant="caption" tone="inkMuted" style={styles.centerText}>
          내용 확인이 끝나면 뽑은 내용을 확인하고 첫 학습을 준비해요.
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
      contentContainerStyle={[styles.content, { paddingTop: insets.top + spacing['3xl'], paddingBottom: tabBarSpace }]}>
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
            ChatGPT·Claude·Codex 대화의 공유 버튼으로 만든 링크를 붙여넣어 주세요.
          </ThemedText>
          <GuideLink label="공유 링크 만드는 법" url={url} />
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
            붙여넣은 대화는 원문 그대로 저장돼요. 저장 뒤 뽑은 학습 내용을 확인할 수 있어요.
          </ThemedText>
        </View>
      )}

      {error && (
        <View accessibilityRole="alert" style={styles.error}>
          <ThemedText variant="subhead" tone="dangerInk">
            {error.message}
          </ThemedText>
          {mode === 'share_link' && <GuideLink label="공유 링크를 맞게 만들었는지 확인하기" url={url} />}
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

      {needsAgreement && (
        <View style={styles.agreement}>
          <ThemedText variant="headline">학습 내용을 만들려면 대화를 저장해야 해요</ThemedText>
          <ThemedText variant="subhead" tone="inkSecondary">
            저장하기 전에 아래 내용에 동의해 주세요. {AI_NOTICE}
          </ThemedText>
          <View style={styles.agreementLinks}>
            {AGREEMENT_LINKS.map((link) => (
              <Pressable key={link.href} accessibilityRole="link" hitSlop={8} onPress={() => router.push(link.href)}>
                <ThemedText variant="subhead" tone="primaryInk">
                  {link.label}
                </ThemedText>
              </Pressable>
            ))}
          </View>
          {agreeError && (
            <ThemedText variant="caption" tone="dangerInk">
              동의를 저장하지 못했어요. 다시 시도해 주세요.
            </ThemedText>
          )}
        </View>
      )}

      <View style={styles.submit}>
        <Button
          title={needsAgreement ? '동의하고 학습 내용 만들기' : '학습 내용 만들기'}
          disabled={value.length === 0}
          loading={agreeing || submit.isPending}
          onPress={send}
        />
        {submit.isPending && (
          <ThemedText variant="caption" tone="inkMuted" style={styles.centerText}>
            대화에서 학습 내용을 뽑는 중이에요. 30초쯤 걸릴 수 있어요.
          </ThemedText>
        )}
      </View>

      {/* 앱의 기본 입력 경로는 Claude 커넥터다(스펙 §7.6). 링크·붙여넣기는 커넥터를 못 쓸 때의 대안이다.
          토스 인앱(#149)은 토스 로그인 계정이라 커넥터(구글·카카오 로그인)를 쓸 수 없어 안내하지 않는다. */}
      {!inToss && (
        <View style={styles.tip}>
          <View style={styles.tipIcon}>
            <Icon name="message-circle" color={colors.primaryInk} />
          </View>
          <View style={styles.tipText}>
            <ThemedText variant="headline">Claude에서 바로 보낼 수도 있어요</ThemedText>
            <ThemedText variant="subhead" tone="inkSecondary">
              커넥터를 연결해 두면 대화 중에 “복습에 넣어줘”라고만 말해도 여기로 들어와요.
            </ThemedText>
          </View>
        </View>
      )}
    </ScrollView>
  );
}

/** 공유 링크 만드는 법(#168)으로 간다. 입력한 링크로 서비스를 알 수 있으면 그 서비스 안내로 바로 간다. */
function GuideLink({ label, url }: { label: string; url: string }) {
  const source = guessShareSource(url);
  return (
    <Pressable
      accessibilityRole="link"
      hitSlop={8}
      onPress={() =>
        source ? router.push({ pathname: '/share-guide/[source]', params: { source } }) : router.push('/share-guide')
      }
      style={styles.guideLink}>
      <Icon name="help-circle" size="sm" color={colors.primaryInk} />
      <ThemedText variant="subhead" tone="primaryInk">
        {label}
      </ThemedText>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  content: { padding: spacing.xl, gap: spacing['2xl'] },
  intro: { gap: spacing.sm },
  tip: {
    flexDirection: 'row',
    gap: spacing.md,
    padding: spacing.xl,
    borderRadius: components.card.rounded,
    borderCurve: 'continuous',
    backgroundColor: colors.surfaceSoft,
  },
  tipIcon: {
    width: components.iconCircle.size,
    height: components.iconCircle.size,
    borderRadius: components.iconCircle.rounded,
    backgroundColor: components.iconCircle.backgroundColor,
    alignItems: 'center',
    justifyContent: 'center',
  },
  tipText: { flex: 1, gap: spacing.xs },
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
  guideLink: { flexDirection: 'row', alignItems: 'center', alignSelf: 'flex-start', gap: spacing.xs },
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
  agreement: {
    gap: spacing.sm,
    padding: spacing.xl,
    borderRadius: components.card.rounded,
    borderCurve: 'continuous',
    backgroundColor: colors.surfaceSoft,
  },
  agreementLinks: { flexDirection: 'row', flexWrap: 'wrap', columnGap: spacing.lg, rowGap: spacing.xs },
});
