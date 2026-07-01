import React from 'react';
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { getToken, removeToken } from '@/utils/request';
import { getCurrentUser } from '@/services/auth';

/**
 * 全局样式
 */
import '@/global.css';

/**
 * 运行时 ConfigProvider 兜底。
 */
export function rootContainer(container: React.ReactNode) {
  return (
    <ConfigProvider
      locale={zhCN}
      theme={{
        token: {
          colorPrimary: '#D97757',
          colorBgContainer: '#FAF9F6',
          colorText: '#1A1915',
          borderRadius: 6,
          fontFamily: 'system-ui, -apple-system, sans-serif',
        },
      }}
    >
      {container}
    </ConfigProvider>
  );
}

/**
 * 路由守卫：未登录跳转到 /login。
 */
export function onRouteChange({ location }: { location: { pathname: string } }) {
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
    const user = await getCurrentUser();
    return { currentUser: user };
  } catch {
    removeToken();
    return { currentUser: null };
  }
}
