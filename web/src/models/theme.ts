import defaultTheme from '@/themes/theme-default-light.json';
import { flattenTheme, type LantingToken, type RawTheme } from '@/themes';

// 默认 token，模块加载时解析一次，不重复计算
const DEFAULT_TOKEN: LantingToken = flattenTheme(defaultTheme as RawTheme);

export default () => {
  return DEFAULT_TOKEN;
};