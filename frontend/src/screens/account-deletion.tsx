import { router } from 'expo-router';
import { useState } from 'react';
import { ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { useSession } from '@/api/session';
import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { Icon } from '@/components/icon';
import { Notice } from '@/components/notice';
import { ThemedText } from '@/components/themed-text';
import { colors, spacing } from '@/theme';

import { OPERATOR } from './policies/content';

const DELETED = [
  '받은 학습 대화와 학습 내용',
  '문제와 풀이 기록',
  '기억 상태와 복습 일정',
  '학습 설정·알림·연속 학습 기록',
  'Claude 커넥터 연결',
];

/**
 * 회원 탈퇴 (#148). 로그인했으면 이 화면에서 바로 지우고, 로그인 전(웹 공개 주소 /account-deletion)에는 지우는 방법을 안내한다.
 * 되돌릴 수 없으므로 보조 버튼 → 위험 버튼 두 단계로 묻는다.
 */
export function AccountDeletion() {
  const insets = useSafeAreaInsets();
  const { signedIn, deleteAccount } = useSession();
  const [confirming, setConfirming] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function remove() {
    setDeleting(true);
    setError(null);
    try {
      await deleteAccount();
      router.replace('/welcome');
    } catch {
      setError('탈퇴하지 못했어요. 잠시 뒤 다시 시도해 주세요.');
      setDeleting(false);
    }
  }

  return (
    <ScrollView contentContainerStyle={[styles.content, { paddingBottom: insets.bottom + spacing['3xl'] }]}>
      <View style={styles.intro}>
        <ThemedText variant="title">계정과 학습 기록이 모두 지워져요</ThemedText>
        <ThemedText tone="inkSecondary">
          탈퇴하면 아래 데이터가 바로 지워지고 되돌릴 수 없어요. 같은 계정으로 다시 로그인하면 새 계정으로 시작해요.
        </ThemedText>
      </View>

      <Card style={styles.list}>
        {DELETED.map((item) => (
          <View key={item} style={styles.item}>
            <Icon name="trash-2" size="sm" color={colors.inkSecondary} />
            <ThemedText variant="subhead">{item}</ThemedText>
          </View>
        ))}
      </Card>

      {signedIn ? (
        <View style={styles.actions}>
          {error && <Notice tone="danger">{error}</Notice>}
          {confirming ? (
            <>
              <Notice tone="danger">정말 탈퇴할까요? 지운 데이터는 복구할 수 없어요.</Notice>
              <Button variant="danger" title="모든 데이터 지우고 탈퇴" loading={deleting} onPress={remove} />
              <Button variant="secondary" title="취소" disabled={deleting} onPress={() => setConfirming(false)} />
            </>
          ) : (
            <Button variant="secondary" title="탈퇴하기" onPress={() => setConfirming(true)} />
          )}
        </View>
      ) : (
        <View style={styles.intro}>
          <ThemedText variant="headline">탈퇴하는 방법</ThemedText>
          <ThemedText tone="inkSecondary">앱에 로그인한 뒤 내 정보 → 회원 탈퇴에서 바로 지울 수 있어요.</ThemedText>
          <ThemedText tone="inkSecondary">
            앱을 쓸 수 없으면 로그인한 계정(구글·카카오)을 적어 {OPERATOR.email}로 요청해 주세요. 확인 후 7일 안에 지워요.
          </ThemedText>
        </View>
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  content: { paddingHorizontal: spacing.xl, paddingTop: spacing.lg, gap: spacing['2xl'] },
  intro: { gap: spacing.sm },
  list: { gap: spacing.md },
  item: { flexDirection: 'row', alignItems: 'center', gap: spacing.md },
  actions: { gap: spacing.md },
});
