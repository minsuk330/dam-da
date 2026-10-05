import { router } from 'expo-router';
import { useState, type ReactNode } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { useLoginOptions, useSession, type Provider } from '@/api/session';
import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { Icon } from '@/components/icon';
import { Logo } from '@/components/logo';
import { Notice } from '@/components/notice';
import { ThemedText } from '@/components/themed-text';
import { colors, components, opacity, spacing } from '@/theme';
import { inToss } from '@/toss';

import { AI_NOTICE, INTRO_STEPS } from '../intro-steps';

import { GoogleLogo, KakaoLogo } from './logos';


/**
 * 로그인 전 화면. 웹은 소셜 로그인 화면, 토스 인앱(#149)은 로그인 없이 들어가는 동안의 준비 화면이다
 * (노출 정책: 로그인 전에 주요 기능을 써볼 수 있어야 함. 서비스 소개는 홈의 첫 사용자 화면이 맡는다).
 */
export function Welcome() {
  return inToss ? <TossEntry /> : <SocialWelcome />;
}

/** 토스 인앱 자동 진입(익명 식별키) 중이거나 실패했을 때. 성공하면 보호된 화면 전환으로 홈이 열린다. */
function TossEntry() {
  const { entry, retryEntry } = useSession();
  return (
    <View style={[styles.screen, styles.entry]}>
      <Logo size={components.iconButtonPrimary.size} />
      {entry === 'error' ? (
        <>
          <ThemedText variant="title">담다를 열지 못했어요</ThemedText>
          <ThemedText variant="subhead" tone="inkSecondary" style={styles.note}>
            잠시 후 다시 시도해 주세요.
          </ThemedText>
          <Button variant="secondary" title="다시 시도" onPress={retryEntry} />
        </>
      ) : (
        <>
          <ActivityIndicator color={colors.primary} />
          <ThemedText variant="subhead" tone="inkSecondary">
            담다를 여는 중이에요
          </ThemedText>
        </>
      )}
    </View>
  );
}

/**
 * 웹 메인(로그인 전) 화면 (스펙 §7.9). 서비스가 하는 일을 세 줄로 보여주고 구글·카카오 소셜 로그인만 둔다.
 * 아래에 이용약관·개인정보 처리방침 링크를 둔다(#148).
 */
function SocialWelcome() {
  const insets = useSafeAreaInsets();
  const { signIn } = useSession();
  const options = useLoginOptions();
  const [pending, setPending] = useState<Provider | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function start(provider: Provider) {
    setPending(provider);
    setError(null);
    try {
      await signIn(provider, options.data);
    } catch (e) {
      setError(e instanceof Error ? e.message : '로그인하지 못했어요. 다시 시도해 주세요.');
      setPending(null);
    }
  }

  return (
    <View style={[styles.screen, { paddingTop: insets.top, paddingBottom: insets.bottom + spacing.xl }]}>
      <ScrollView contentContainerStyle={styles.content}>
        <View style={styles.brand}>
          <Logo size={components.iconButtonPrimary.size} />
          <ThemedText variant="title">담다</ThemedText>
        </View>

        <View style={styles.intro}>
          <ThemedText variant="display">{'AI와 나눈 대화,\n잊기 전에 복습해요'}</ThemedText>
          <ThemedText variant="body" tone="inkSecondary">
            공부하며 나눈 대화에서 배운 것을 골라 문제로 만들고, 매일 짧게 복습하게 도와드려요.
          </ThemedText>
          {/* 생성형 AI 사전 고지(앱인토스 서비스 오픈 정책 2-4, #149). */}
          <ThemedText variant="caption" tone="inkMuted">
            {AI_NOTICE}
          </ThemedText>
        </View>

        <Card style={styles.steps}>
          {INTRO_STEPS.map((step, i) => (
            <View key={step.title} style={[styles.step, i > 0 && styles.divided]}>
              <View style={styles.stepIcon}>
                <Icon name={step.icon} color={colors.primaryInk} />
              </View>
              <View style={styles.stepText}>
                <ThemedText variant="headline">{step.title}</ThemedText>
                <ThemedText variant="caption" tone="inkSecondary">
                  {step.detail}
                </ThemedText>
              </View>
            </View>
          ))}
        </Card>
      </ScrollView>

      <View style={styles.footer}>
        {error && <Notice tone="danger">{error}</Notice>}
        {options.isError && <Notice tone="danger">로그인 방법을 불러오지 못했어요. 잠시 뒤 다시 열어 주세요.</Notice>}
        <SocialButton
          provider="kakao"
          label="카카오로 시작하기"
          logo={<KakaoLogo />}
          pending={pending}
          onPress={() => start('kakao')}
        />
        <SocialButton
          provider="google"
          label="Google로 시작하기"
          logo={<GoogleLogo />}
          pending={pending}
          onPress={() => start('google')}
        />
        <ThemedText variant="caption" tone="inkMuted" style={styles.note}>
          처음 로그인하면 계정이 만들어지며,{' '}
          <ThemedText variant="caption" tone="primaryInk" accessibilityRole="link" onPress={() => router.push('/terms')}>
            이용약관
          </ThemedText>
          과{' '}
          <ThemedText variant="caption" tone="primaryInk" accessibilityRole="link" onPress={() => router.push('/privacy')}>
            개인정보 처리방침
          </ThemedText>
          에 동의하는 것으로 봐요.
        </ThemedText>
      </View>
    </View>
  );
}

/** 브랜드 가이드를 따르는 소셜 로그인 버튼. 로고는 왼쪽, 글자는 가운데. 다른 버튼이 진행 중이면 누를 수 없다. */
function SocialButton({
  provider,
  label,
  logo,
  pending,
  onPress,
}: {
  provider: 'kakao' | 'google';
  label: string;
  logo: ReactNode;
  pending: Provider | null;
  onPress: () => void;
}) {
  const kakao = provider === 'kakao';
  const busy = pending === provider;
  const disabled = pending !== null;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityState={{ disabled, busy }}
      disabled={disabled}
      onPress={onPress}
      style={({ pressed }) => [
        styles.social,
        kakao ? styles.kakao : styles.google,
        pressed && (kakao ? styles.kakaoPressed : styles.googlePressed),
        disabled && !busy && styles.disabled,
      ]}>
      <View style={styles.socialLogo}>{logo}</View>
      {busy ? (
        <ActivityIndicator color={kakao ? colors.kakaoInk : colors.ink} />
      ) : (
        <ThemedText variant="headline" tone={kakao ? 'kakaoInk' : 'ink'}>
          {label}
        </ThemedText>
      )}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.canvas },
  entry: { alignItems: 'center', justifyContent: 'center', gap: spacing.lg, padding: spacing.xl },
  content: { paddingHorizontal: spacing.xl, paddingTop: spacing.lg, gap: spacing['2xl'] },
  brand: { flexDirection: 'row', alignItems: 'center', gap: spacing.md },
  intro: { gap: spacing.md },
  steps: { paddingVertical: spacing.xs },
  step: { flexDirection: 'row', alignItems: 'center', gap: spacing.md, paddingVertical: spacing.lg },
  divided: { borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.outline },
  stepIcon: {
    width: components.iconCircle.size,
    height: components.iconCircle.size,
    borderRadius: components.iconCircle.rounded,
    backgroundColor: components.iconCircle.backgroundColor,
    alignItems: 'center',
    justifyContent: 'center',
  },
  stepText: { flex: 1, gap: spacing['2xs'] },
  footer: { paddingHorizontal: spacing.xl, paddingTop: spacing.lg, gap: spacing.sm },
  social: {
    height: components.buttonKakao.height,
    borderRadius: components.buttonKakao.rounded,
    alignItems: 'center',
    justifyContent: 'center',
  },
  // 로고는 왼쪽 끝에 두고 글자는 버튼 가운데에 둔다.
  socialLogo: { position: 'absolute', left: spacing['2xl'] },
  kakao: { backgroundColor: components.buttonKakao.backgroundColor },
  kakaoPressed: { backgroundColor: components.buttonKakaoPressed.backgroundColor },
  google: {
    backgroundColor: components.buttonGoogle.backgroundColor,
    borderWidth: components.buttonGoogleOutline.width,
    borderColor: components.buttonGoogleOutline.backgroundColor,
  },
  googlePressed: { backgroundColor: components.cardPressed.backgroundColor },
  disabled: { opacity: opacity.disabled },
  note: { textAlign: 'center' },
});
