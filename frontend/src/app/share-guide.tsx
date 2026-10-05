import { useLocalSearchParams } from 'expo-router';

import { ShareGuide } from '@/screens/share-guide';
import { SHARE_GUIDES } from '@/screens/share-guide/steps';

export default function ShareGuideRoute() {
  const { source } = useLocalSearchParams<{ source?: string }>();
  return <ShareGuide initialSource={SHARE_GUIDES.find((g) => g.source === source)?.source} />;
}
