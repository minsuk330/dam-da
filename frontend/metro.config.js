// https://docs.expo.dev/guides/customizing-metro
const { getDefaultConfig } = require('expo/metro-config');

const config = getDefaultConfig(__dirname);

// dotLottie 애니메이션(assets/animations/*.lottie)을 자산으로 번들한다.
config.resolver.assetExts.push('lottie');

module.exports = config;
