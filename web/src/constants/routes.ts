// 路由路径常量
// 避免在代码里写字面量字符串，改动路由只需改这里

export const ROUTES = {
  HOME:       '/',
  LOGIN:      '/login',
  EDITOR:     '/editor',
  JOBS:       '/jobs',
  DATASOURCE: '/datasource',
  CLUSTER:    '/cluster',
  USERS:      '/users',
} as const;
