/**
 * 토스 인앱 빌드 전용(#149): expo-modules-core uuid(web)에서 Node 환경용 eval('require') 분기를 뺀 판이다.
 * 앱인토스 검수는 번들 안의 eval을 반려한다. 브라우저 WebView에는 crypto.randomUUID가 있다. metro.config.js가 바꿔 끼운다.
 */
import sha1 from '../../node_modules/expo-modules-core/src/uuid/lib/sha1';
import v35 from '../../node_modules/expo-modules-core/src/uuid/lib/v35';
import { Uuidv5Namespace } from '../../node_modules/expo-modules-core/src/uuid/uuid.types';

const uuid = {
  v4: (): string => crypto.randomUUID(),
  v5: v35('v5', 0x50, sha1),
  namespace: Uuidv5Namespace,
};

export default uuid;
