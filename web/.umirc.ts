import { defineConfig } from 'umi';

export default defineConfig({
  plugins: ['@umijs/plugins/dist/antd', '@umijs/plugins/dist/initial-state', '@umijs/plugins/dist/model'],

  initialState: {},
  model: {},

  antd: {
    configProvider: {},
    theme: {
      token: {
        // 品牌色 — clay 橙
        colorPrimary: '#D97757',
        colorPrimaryHover: '#C86A47',
        colorLink: '#D97757',
        colorLinkHover: '#C86A47',

        // 字体
        fontFamily: 'system-ui, -apple-system, sans-serif',
        fontSize: 14,

        // 圆角：按钮/输入框 6px，模态框 12px
        borderRadius: 6,
        borderRadiusLG: 12,

        // 边框
        colorBorder: '#E8E5DF',
        colorBorderSecondary: '#D8D4CC',

        // 背景 — 米白体系
        colorBgContainer: '#FAF9F6',
        colorBgLayout: '#FDFCF9',
        colorBgElevated: '#FAF9F6',

        // 文字 — 暖黑层次
        colorText: '#1A1915',
        colorTextSecondary: '#5F5E5A',
        colorTextDescription: '#9B9689',
        colorTextDisabled: '#C4C0B8',

        // 阴影（仅弹窗）
        boxShadow: '0 4px 16px rgba(0,0,0,0.08)',
        boxShadowSecondary: '0 8px 32px rgba(0,0,0,0.12)',
      },
      components: {
        Table: {
          headerBg: '#F5F3EE',
          rowHoverBg: '#FDEEE8',
          borderColor: '#E8E5DF',
        },
        Modal: {
          borderRadiusLG: 12,
        },
        Menu: {
          itemBorderRadius: 6,
          itemSelectedBg: '#FDEEE8',
          itemSelectedColor: '#D97757',
        },
        Input: {
          borderRadius: 6,
          activeBorderColor: '#D97757',
          hoverBorderColor: '#D8D4CC',
        },
        Button: {
          borderRadius: 6,
          primaryShadow: 'none',
          defaultShadow: 'none',
        },
        Select: {
          borderRadius: 6,
        },
        Tag: {
          borderRadiusSM: 4,
        },
      },
    },
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
