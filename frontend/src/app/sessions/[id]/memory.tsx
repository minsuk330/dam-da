import { useLocalSearchParams } from 'expo-router';

import { SessionMemory } from '@/screens/session-memory';

export default function SessionMemoryRoute() {
  const { id } = useLocalSearchParams<{ id: string }>();
  return <SessionMemory id={Number(id)} />;
}
