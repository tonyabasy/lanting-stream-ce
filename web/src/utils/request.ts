import axios, { AxiosResponse } from 'axios';

// ==================== 统一错误类型 ====================

export class ApiError extends Error {
  code: number;

  constructor(code: number, message: string) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
  }
}

// ==================== Token 管理 ====================

const TOKEN_KEY = 'lanting-token';

export const getToken = (): string | null => localStorage.getItem(TOKEN_KEY);
export const setToken = (token: string): void => localStorage.setItem(TOKEN_KEY, token);
export const removeToken = (): void => localStorage.removeItem(TOKEN_KEY);

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
    if (data.code === 0) {
      return data.data;
    }
    return Promise.reject(new ApiError(data.code, data.message || '请求失败'));
  },
  (error) => {
    if (error.response) {
      const { status, data: body } = error.response;

      // 401：登录过期
      if (status === 401) {
        removeToken();
        const redirect = encodeURIComponent(
          window.location.pathname + window.location.search,
        );
        window.location.href = `/login?redirect=${redirect}`;
        return Promise.reject(new ApiError(body?.code || 20001, body?.message || '登录已过期，请重新登录'));
      }

      // 403：无权限
      if (status === 403) {
        return Promise.reject(new ApiError(20002, '没有权限'));
      }

      // 400：参数校验失败 / 业务规则冲突
      if (status === 400) {
        const msg = body?.message || '参数错误';
        return Promise.reject(new ApiError(body?.code || 10001, msg));
      }

      // 500+：系统内部错误，不暴露详情
      if (status >= 500) {
        return Promise.reject(new ApiError(50001, '服务器异常，请稍后重试'));
      }
    }

    if (error.code === 'ECONNABORTED') {
      return Promise.reject(new ApiError(10002, '请求超时'));
    }
    // 网络层错误（ECONNREFUSED、ECONNRESET 等），无 HTTP 响应，前端内部处理
    return Promise.reject(new ApiError(-1, '网络连接失败'));
  },
);

export default request;
