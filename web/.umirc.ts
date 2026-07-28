import { defineConfig } from 'umi';

export default defineConfig({
  mock: false,
  plugins: [
    '@umijs/plugins/dist/antd',
    '@umijs/plugins/dist/initial-state',
    '@umijs/plugins/dist/model',
    '@umijs/plugins/dist/locale',
  ],

  initialState: {},
  model: {},

  locale: {
    default: 'zh-CN',
    useLocalStorage: true,
    baseNavigator: true,
  },

  antd: {},

  // 路由
  routes: [
    { path: '/login', component: 'login', layout: false },
    { path: '/', component: 'index' },
    { path: '/dev', component: 'dev', layout: false },
    { path: '/datasource', component: 'index' },
    { path: '/cluster', component: 'cluster/index' },
    { path: '/users', component: 'index' },
    { path: '/ops', component: 'ops/index' },
  ],

  // 代理：开发时 /api 转发到后端 8080
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true,
    },
  },

  npmClient: 'pnpm',
});
