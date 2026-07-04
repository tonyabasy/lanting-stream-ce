import axios, { AxiosResponse } from 'axios';
import { getIntl, getLocale } from 'umi';

// ==================== 统一错误类型 ====================

export class ApiError extends Error {
  code: number;

  constructor(code: number, message: string) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
  }
}

// ==================== 兜底文案 ====================

function getFallbackMessage(key: string): string {
  const intl = getIntl(getLocale() || 'zh-CN');
  return intl.formatMessage({
    id: key,
    defaultMessage: intl.formatMessage({ id: 'error.default' }),
  });
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

// --- 请求拦截器：注入 Sa-Token JWT token 和当前语言 ---
request.interceptors.request.use(
  (config) => {
    const token = getToken();
    if (token) {
      config.headers['lanting-token'] = token;
    }
    config.headers['Accept-Language'] = getLocale() || 'zh-CN';
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
    return Promise.reject(
      new ApiError(data.code, data.message || getFallbackMessage('error.default')),
    );
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
        return Promise.reject(
          new ApiError(
            body?.code || 20001,
            body?.message || getFallbackMessage('error.default'),
          ),
        );
      }

      // 403：无权限
      if (status === 403) {
        return Promise.reject(
          new ApiError(
            20002,
            body?.message || getFallbackMessage('error.default'),
          ),
        );
      }

      // 400：参数校验失败 / 业务规则冲突
      if (status === 400) {
        const msg = body?.message || getFallbackMessage('error.default');
        return Promise.reject(new ApiError(body?.code || 10001, msg));
      }

      // 404：资源不存在
      if (status === 404) {
        return Promise.reject(
          new ApiError(
            body?.code || 404,
            body?.message || getFallbackMessage('error.notFound'),
          ),
        );
      }

      // 500+：系统内部错误，不暴露详情
      if (status >= 500) {
        return Promise.reject(
          new ApiError(
            50001,
            body?.message || getFallbackMessage('error.server'),
          ),
        );
      }
    }

    if (error.code === 'ECONNABORTED') {
      return Promise.reject(new ApiError(10002, getFallbackMessage('error.timeout')));
    }
    // 网络层错误（ECONNREFUSED、ECONNRESET 等），无 HTTP 响应，前端内部处理
    return Promise.reject(new ApiError(-1, getFallbackMessage('error.network')));
  },
);

export default request;
