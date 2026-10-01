import { useState, type ReactNode } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { useLoginOptions, useSession, type Provider } from '@/api/session';
import { Card } from '@/components/card';
import { Icon, type IconName } from '@/components/icon';
import { Logo } from '@/components/logo';
import { Notice } from '@/components/notice';
import { ThemedText } from '@/components/themed-text';
import { colors, components, opacity, spacing } from '@/theme';

import { GoogleLogo, KakaoLogo } from './logos';

const STEPS: { icon: IconName; title: string; detail: string }[] = [
  { icon: 'message-circle', title: 'Claude와 나눈 대화를 보내요', detail: '커넥터나 공유 링크로 한 번에 담아요' },
  { icon: 'check-square', title: '배운 내용을 문제로 확인해요', detail: '대화 속 내 질문에서 나온 것만 물어봐요' },
  { icon: 'calendar', title: '잊을 때쯤 매일 몇 분 복습해요', detail: '기억 상태를 계산해 복습할 때를 골라요' },
];

/**
 * 앱 메인(로그인 전) 화면 (스펙 §7.9). 서비스가 하는 일을 세 줄로 보여주고 구글·카카오 소셜 로그인만 둔다.
 * "데모 계정으로 시작"은 개발 도구가 켜진 서버에서만 보인다.
 */
export function Welcome() {
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
        </View>

        <Card style={styles.steps}>
          {STEPS.map((step, i) => (
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
          처음 로그인하면 계정이 만들어져요.
        </ThemedText>
        {options.data?.demoLogin && (
          <Pressable
            accessibilityRole="button"
            accessibilityState={{ disabled: pending !== null }}
            disabled={pending !== null}
            hitSlop={8}
            onPress={() => start('demo')}
            style={styles.demo}>
            <ThemedText variant="subhead" tone="primaryInk">
              {pending === 'demo' ? '데모 계정으로 들어가는 중…' : '데모 계정으로 시작'}
            </ThemedText>
          </Pressable>
        )}
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
  demo: { alignSelf: 'center', paddingVertical: spacing.sm },
});
