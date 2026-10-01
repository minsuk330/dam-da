import type { Tabs } from 'expo-router';
import type { ComponentProps } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Icon, type IconName } from '@/components/icon';
import { ThemedText } from '@/components/themed-text';
import { colors, components, spacing } from '@/theme';

// expo-router가 bottom-tabs 타입을 공개 경로로 내보내지 않아 Tabs의 tabBar prop에서 꺼낸다.
type BottomTabBarProps = Parameters<NonNullable<ComponentProps<typeof Tabs>['tabBar']>>[0];

/** 라우트 이름 → 아이콘·라벨. 탭을 추가하면 여기에도 추가한다. */
const TABS: Record<string, { icon: IconName; label: string }> = {
  index: { icon: 'home', label: '홈' },
  conversations: { icon: 'message-circle', label: '대화' },
};

/**
 * 하단에 붙은 불투명 탭바. 레퍼런스의 떠 있는 유리 탭바 대신(DESIGN.md Don'ts),
 * 활성 탭은 inverse 원으로 표시한다.
 */
export function TabBar({ state, navigation }: BottomTabBarProps) {
  const insets = useSafeAreaInsets();
  return (
    <View style={[styles.bar, { paddingBottom: Math.max(insets.bottom, spacing.md) }]}>
      {state.routes.map((route, index) => {
        const tab = TABS[route.name];
        if (!tab) return null;
        const focused = state.index === index;
        return (
          <Pressable
            key={route.key}
            accessibilityRole="tab"
            accessibilityLabel={tab.label}
            accessibilityState={{ selected: focused }}
            onPress={() => {
              const event = navigation.emit({ type: 'tabPress', target: route.key, canPreventDefault: true });
              if (!focused && !event.defaultPrevented) navigation.navigate(route.name);
            }}
            style={styles.tab}>
            <View style={[styles.circle, focused && styles.circleActive]}>
              <Icon name={tab.icon} color={focused ? components.tabActive.textColor : colors.inkMuted} />
            </View>
            <ThemedText variant="caption" tone={focused ? 'ink' : 'inkMuted'}>
              {tab.label}
            </ThemedText>
          </Pressable>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  bar: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    paddingTop: spacing.md,
    backgroundColor: components.tabBar.backgroundColor,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: colors.outline,
  },
  tab: { alignItems: 'center', gap: spacing.xs, minWidth: 64 },
  circle: {
    width: 48,
    height: 48,
    borderRadius: components.tabActive.rounded,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: colors.surfaceSoft,
  },
  circleActive: { backgroundColor: components.tabActive.backgroundColor },
});
