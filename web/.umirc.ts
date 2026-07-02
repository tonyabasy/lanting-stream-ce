import { defineConfig } from 'umi';

export default defineConfig({
  plugins: ['@umijs/plugins/dist/antd', '@umijs/plugins/dist/initial-state', '@umijs/plugins/dist/model'],

  initialState: {},
  model: {},

  antd: {
    configProvider: {},
  },

  // 路由
  routes: [
    { path: '/login', component: 'login', layout: false },
    {
      path: '/',
      component: '@/layouts/index',
      routes: [
        { path: '/', component: 'index' },
        { path: '/editor', component: 'editor/index' },
        { path: '/datasource', component: 'index' },
        { path: '/cluster', component: 'index' },
        { path: '/users', component: 'index' },
      ],
    },
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
