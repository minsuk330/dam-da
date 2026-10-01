import type { ReactNode } from 'react';

/** 네이티브에서는 기기 화면이 곧 앱 화면이다. 웹 전용 처리는 phone-frame.web.tsx. */
export function PhoneFrame({ children }: { children: ReactNode }) {
  return children;
}
