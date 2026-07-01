import request from '@/utils/request';

export interface LoginParams {
  username: string;
  password: string;
}

export interface LoginResult {
  id: number;
  username: string;
  nickname: string;
  email: string;
  avatarUrl: string;
  superAdminFlag: boolean;
  tokenInfo: {
    token: string;
    tokenTtl: number;
    tokenExpireAt: number;
  };
}

export const login = (params: LoginParams): Promise<LoginResult> =>
  request.post('/auth/login', params);

export const logout = (): Promise<void> =>
  request.post('/auth/logout');

export const getCurrentUser = (): Promise<LoginResult> =>
  request.get('/auth/current');
