// Metro가 .lottie 파일을 자산으로 번들한다(metro.config.js). import 값은 자산 모듈 ID다.
declare module '*.lottie' {
  const asset: number;
  export default asset;
}
