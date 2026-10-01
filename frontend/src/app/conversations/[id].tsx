import { useLocalSearchParams } from 'expo-router';

import { ConversationDetail } from '@/screens/conversation-detail';

export default function ConversationDetailRoute() {
  const { id } = useLocalSearchParams<{ id: string }>();
  return <ConversationDetail id={id} />;
}
