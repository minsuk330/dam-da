import { useLocalSearchParams } from 'expo-router';

import { Review } from '@/screens/review';

export default function ReviewRoute() {
  const { practiceId } = useLocalSearchParams<{ practiceId?: string }>();
  const id = Number(practiceId);
  return <Review key={practiceId} practiceId={Number.isInteger(id) && id > 0 ? id : null} />;
}
