import { useLocalSearchParams } from 'expo-router';

import { Review } from '@/screens/review';

const idOf = (param: string | undefined) => {
  const id = Number(param);
  return Number.isInteger(id) && id > 0 ? id : null;
};

export default function ReviewRoute() {
  const { practiceId, sessionId } = useLocalSearchParams<{ practiceId?: string; sessionId?: string }>();
  return <Review key={practiceId} practiceId={idOf(practiceId)} sessionId={idOf(sessionId)} />;
}
