import { Redirect, useLocalSearchParams } from 'expo-router';

import { findGuide } from '@/screens/share-guide/steps';
import { ShareGuideWalkthrough } from '@/screens/share-guide/walkthrough';

export default function ShareGuideSourceRoute() {
  const { source } = useLocalSearchParams<{ source: string }>();
  const guide = findGuide(source);
  return guide ? <ShareGuideWalkthrough key={guide.source} guide={guide} /> : <Redirect href="/share-guide" />;
}
