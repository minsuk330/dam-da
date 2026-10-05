/**
 * 토스 인앱 빌드 전용(#149): Expo 비동기 로딩의 fetch + eval 경로를 대신한다.
 * 앱인토스 검수는 번들 안에 "외부에서 받은 코드를 eval로 실행"하는 코드가 있으면 반려한다. 운영 웹 빌드는
 * <script> 태그로 청크를 불러오고 이 경로는 개발 서버에서만 쓰므로, 호출되면 오류를 낸다. metro.config.js가 바꿔 끼운다.
 */
export function fetchThenEvalAsync(url: string): Promise<void> {
  return Promise.reject(new Error(`이 빌드에서는 코드를 내려받아 실행하지 않아요: ${url}`));
}
