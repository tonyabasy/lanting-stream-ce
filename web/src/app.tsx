import { getToken, removeToken } from '@/utils/request';
import '@/global.css';

/**
 * 路由守卫：未登录跳转到 /login。
 */
export function onRouteChange({ location }: { location: { pathname: string } }) {
  // if (process.env.NODE_ENV === 'development') return; // 开发时不守卫
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
