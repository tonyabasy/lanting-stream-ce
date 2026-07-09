import { DEFAULT_TOKEN } from '@/themes';

// 默认 token，模块加载时解析一次，不重复计算
export default () => {
  return DEFAULT_TOKEN;
};