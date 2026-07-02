import { defineConfig } from 'umi';

export default defineConfig({
  plugins: ['@umijs/plugins/dist/antd', '@umijs/plugins/dist/initial-state', '@umijs/plugins/dist/model'],

  initialState: {},
  model: {},

  antd: {},

  // 路由
  routes: [
    { path: '/login', component: 'login', layout: false },
    { path: '/', component: 'index' },
    { path: '/editor', component: 'editor/index' },
    { path: '/datasource', component: 'index' },
    { path: '/cluster', component: 'index' },
    { path: '/users', component: 'index' },
    { path: '/ops', component: 'ops/index' },
    { path: '/design/login', component: 'design/login' },
    { path: '/design/cluster', component: 'design/cluster' },
    { path: '/design/editor', component: 'design/editor' },
    { path: '/design/theme-preview', component: 'design/theme-preview' },
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
