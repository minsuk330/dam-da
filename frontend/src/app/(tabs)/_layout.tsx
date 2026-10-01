import { Tabs } from 'expo-router';

import { TabBar } from '@/components/tab-bar';
import { colors, typography } from '@/theme';

export default function TabsLayout() {
  return (
    <Tabs
      tabBar={(props) => <TabBar {...props} />}
      screenOptions={{
        headerShadowVisible: false,
        headerTitleAlign: 'center',
        headerStyle: { backgroundColor: colors.canvas },
        headerTintColor: colors.ink,
        headerTitleStyle: { fontFamily: typography.headline.fontFamily, fontSize: typography.headline.fontSize },
        sceneStyle: { backgroundColor: colors.canvas },
      }}>
      <Tabs.Screen name="index" options={{ headerShown: false }} />
      <Tabs.Screen name="memory" options={{ title: '기억' }} />
      <Tabs.Screen name="add" options={{ headerShown: false }} />
    </Tabs>
  );
}
