import type { Tabs } from 'expo-router';
import type { ComponentProps } from 'react';
import { Platform, Pressable, StyleSheet, View, type ViewStyle } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Icon, type IconName } from '@/components/icon';
import { components, spacing } from '@/theme';

// expo-router가 bottom-tabs 타입을 공개 경로로 내보내지 않아 Tabs의 tabBar prop에서 꺼낸다.
type BottomTabBarProps = Parameters<NonNullable<ComponentProps<typeof Tabs>['tabBar']>>[0];

/** 라우트 이름 → 아이콘·접근성 라벨. 탭을 추가하면 여기에도 추가한다. */
const TABS: Record<string, { icon: IconName; label: string }> = {
  index: { icon: 'home', label: '홈' },
  memory: { icon: 'bar-chart-2', label: '기억' },
  add: { icon: 'plus', label: '대화 추가' },
};

const BAR_HEIGHT = components.tabItem.size + components.tabBar.padding * 2;

function bottomOffset(insetBottom: number) {
  return Math.max(insetBottom, spacing.lg);
}

/** 탭 화면의 스크롤 내용이 떠 있는 탭바에 가리지 않도록 아래에 둘 여백. */
export function useTabBarSpace() {
  const insets = useSafeAreaInsets();
  return BAR_HEIGHT + bottomOffset(insets.bottom) + spacing.lg;
}

/** DESIGN.md의 탭바: 화면 아래 가운데에 떠 있는 반투명 유리 알약. 아이콘만, 활성 탭은 inverse 원. */
export function TabBar({ state, navigation }: BottomTabBarProps) {
  const insets = useSafeAreaInsets();
  return (
    <View pointerEvents="box-none" style={[styles.anchor, { bottom: bottomOffset(insets.bottom) }]}>
      <View accessibilityRole="tablist" style={[styles.bar, glass]}>
        {state.routes.map((route, index) => {
          const tab = TABS[route.name];
          if (!tab) return null;
          const focused = state.index === index;
          const token = focused ? components.tabActive : components.tabItem;
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
              style={[styles.item, { backgroundColor: token.backgroundColor }]}>
              <Icon name={tab.icon} size={22} color={token.textColor} />
            </Pressable>
          );
        })}
      </View>
    </View>
  );
}

// 뒤 배경 흐림은 웹만 CSS로 한다. 네이티브는 반투명 면만 쓴다(DESIGN.md 탭바).
const glass = Platform.select<ViewStyle>({ web: { backdropFilter: 'blur(20px)' } as ViewStyle, default: {} });

const styles = StyleSheet.create({
  anchor: { position: 'absolute', left: 0, right: 0, alignItems: 'center' },
  bar: {
    flexDirection: 'row',
    gap: spacing.sm,
    padding: components.tabBar.padding,
    borderRadius: components.tabBar.rounded,
    backgroundColor: components.tabBar.backgroundColor,
  },
  item: {
    width: components.tabItem.size,
    height: components.tabItem.size,
    borderRadius: components.tabItem.rounded,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
