import { useSession } from '@/api/session'

/** 화면에 보일 로그인 사용자 이름과 머리글자. 서버가 준 이름(소셜 닉네임)을 쓰고, 없으면 "나"로 둔다. */
export function useProfile() {
  const name = useSession().user?.name?.trim() || '나'
  return { name, initial: Array.from(name)[0] }
}
