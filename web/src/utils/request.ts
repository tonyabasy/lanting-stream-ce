import axios, { AxiosResponse } from 'axios';

// ==================== Token 管理 ====================

const TOKEN_KEY = 'lanting-token';

export const getToken = (): string | null => localStorage.getItem(TOKEN_KEY);
export const setToken = (token: string): void => localStorage.setItem(TOKEN_KEY, token);
export const removeToken = (): void => localStorage.removeItem(TOKEN_KEY);

// ==================== 跳转锁 ====================

let isRedirecting = false;

// ==================== Axios 实例 ====================

const request = axios.create({
  baseURL: '/api',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
});

// --- 请求拦截器：注入 Sa-Token JWT token ---
request.interceptors.request.use(
  (config) => {
    const token = getToken();
    if (token) {
      config.headers['lanting-token'] = token;
    }
    return config;
  },
  (error) => Promise.reject(error),
);

// --- 响应拦截器：统一错误处理 ---
request.interceptors.response.use(
  (response: AxiosResponse) => {
    const { data } = response;
    // 后端统一返回 { code: 0, message: "成功", data: ... }
    if (data.code === 0) {
      return data.data;
    }
    return Promise.reject(new Error(data.message || '请求失败'));
  },
  (error) => {
    if (error.response) {
      const { status } = error.response;
      if (status === 401) {
        if (!isRedirecting) {
          isRedirecting = true;
          removeToken();
          // 带 redirect 参数，登录成功后跳回当前页
          const redirect = encodeURIComponent(
            window.location.pathname + window.location.search,
          );
          window.location.href = `/login?redirect=${redirect}`;
        }
        return Promise.reject(new Error('登录已过期，请重新登录'));
      }
      if (status === 403) {
        return Promise.reject(new Error('没有权限'));
      }
      if (status >= 500) {
        return Promise.reject(new Error('服务器异常，请稍后重试'));
      }
    }
    if (error.code === 'ECONNABORTED') {
      return Promise.reject(new Error('请求超时'));
    }
    return Promise.reject(error);
  },
);

export default request;
