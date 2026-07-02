import type { RuntimeAntdConfig } from 'umi';
import zhCN from 'antd/locale/zh_CN';
import { getToken, removeToken } from '@/utils/request';
import { theme as themeBaoLan, injectCSS } from '@/themes/theme-baolan';
import '@/global.css';

// 模块加载时立即注入 CSS 变量，避免首屏闪烁
injectCSS();

/**
 * antd 运行时配置：设置主题和中文 locale。
 */
export const antd: RuntimeAntdConfig = (memo) => {
  memo.theme = themeBaoLan;
  memo.locale = zhCN;
  return memo;
};

/**
 * 路由守卫：未登录跳转到 /login。
 */
export function onRouteChange({ location }: { location: { pathname: string } }) {
  if (process.env.NODE_ENV === 'development') return; // 开发时不守卫
  const token = getToken();
  if (!token && location.pathname !== '/login') {
    removeToken();
    window.location.href = '/login';
  }
}

/**
 * 全局初始状态：登录后存储当前用户信息，供所有页面通过 useModel('@@initialState') 访问。
 */
export async function getInitialState() {
  const token = getToken();
  if (!token) return { currentUser: null };

  try {
    const { getCurrentUser } = await import('@/services/auth');
    const user = await getCurrentUser();
    return { currentUser: user };
  } catch {
    removeToken();
    return { currentUser: null };
  }
}
