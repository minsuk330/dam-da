import { router, Stack } from 'expo-router';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, View } from 'react-native';

import type { Schemas } from '@/api/client';
import { useMarkAllNotificationsRead, useMarkNotificationRead, useNotifications } from '@/api/notifications';
import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { Icon } from '@/components/icon';
import { ThemedText } from '@/components/themed-text';
import { formatDateTime } from '@/labels';
import { colors, components, spacing } from '@/theme';

type Notification = Schemas['NotificationView'];

/** 알림을 누르면 가는 곳. 학습 내용 도착 알림은 그 세션의 확인 화면으로 간다. */
function open(notification: Notification) {
  if (notification.type === 'SESSION_READY') {
    router.push({ pathname: '/sessions/[id]', params: { id: String(notification.targetId) } });
  }
}

/** 앱 안 알림 목록 (스펙 §7.7). 최근 것부터, 읽지 않은 알림은 점과 굵은 제목으로 구분한다. */
export function Notifications() {
  const { data, isPending, isError, refetch } = useNotifications();
  const markRead = useMarkNotificationRead();
  const markAll = useMarkAllNotificationsRead();

  if (isPending) {
    return (
      <View style={styles.center}>
        <ActivityIndicator color={colors.primary} />
      </View>
    );
  }
  if (isError) {
    return (
      <View style={styles.center}>
        <ThemedText tone="dangerInk">알림을 불러오지 못했어요.</ThemedText>
        <Button variant="secondary" title="다시 시도" onPress={() => refetch()} />
      </View>
    );
  }
  if (data.items.length === 0) {
    return (
      <View style={styles.center}>
        <Icon name="bell" size="xl" color={colors.inkMuted} />
        <ThemedText variant="headline">아직 알림이 없어요</ThemedText>
        <ThemedText variant="subhead" tone="inkMuted" style={styles.centerText}>
          대화를 추가하면 학습 내용이 준비됐을 때 알려드릴게요.
        </ThemedText>
      </View>
    );
  }

  return (
    <ScrollView contentContainerStyle={styles.content}>
      <Stack.Screen
        options={{
          headerRight: () =>
            data.unreadCount > 0 ? (
              <Pressable
                accessibilityRole="button"
                onPress={() => markAll.mutate()}
                hitSlop={8}
                style={styles.headerAction}>
                <ThemedText variant="subhead" tone="primaryInk">
                  모두 읽음
                </ThemedText>
              </Pressable>
            ) : null,
        }}
      />
      <Card style={styles.list}>
        {data.items.map((notification, i) => (
          <Pressable
            key={notification.id}
            accessibilityRole="button"
            accessibilityLabel={`${notification.read ? '' : '새 알림, '}${notification.title}. ${notification.body}`}
            onPress={() => {
              if (!notification.read) markRead.mutate(notification.id);
              open(notification);
            }}
            style={({ pressed }) => [styles.row, i > 0 && styles.divided, pressed && styles.rowPressed]}>
            <View style={styles.rowIcon}>
              <Icon name="book-open" color={colors.primaryInk} />
            </View>
            <View style={styles.rowText}>
              <ThemedText
                variant={notification.read ? 'subhead' : 'headline'}
                tone={notification.read ? 'inkSecondary' : 'ink'}>
                {notification.title}
              </ThemedText>
              <ThemedText variant="caption" tone="inkMuted">
                {notification.body} · {formatDateTime(notification.createdAt)}
              </ThemedText>
            </View>
            {!notification.read && <View accessibilityElementsHidden style={styles.unreadDot} />}
          </Pressable>
        ))}
      </Card>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: spacing.md, padding: spacing.xl },
  centerText: { textAlign: 'center' },
  content: { padding: spacing.xl },
  headerAction: { paddingHorizontal: spacing.lg },
  list: { paddingVertical: spacing.xs },
  row: { flexDirection: 'row', alignItems: 'center', gap: spacing.md, paddingVertical: spacing.lg },
  divided: { borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.outline },
  rowPressed: { backgroundColor: components.cardPressed.backgroundColor },
  rowIcon: {
    width: components.iconCircle.size,
    height: components.iconCircle.size,
    borderRadius: components.iconCircle.rounded,
    backgroundColor: components.iconCircle.backgroundColor,
    alignItems: 'center',
    justifyContent: 'center',
  },
  rowText: { flex: 1, gap: spacing['2xs'] },
  unreadDot: {
    width: components.warningMark.size,
    height: components.warningMark.size,
    borderRadius: components.warningMark.size,
    backgroundColor: colors.danger,
  },
});
