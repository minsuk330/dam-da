import { useLocalSearchParams } from 'expo-router';

import { SessionConfirm } from '@/screens/session-confirm';

export default function SessionConfirmRoute() {
  const { id } = useLocalSearchParams<{ id: string }>();
  return <SessionConfirm id={Number(id)} />;
}
