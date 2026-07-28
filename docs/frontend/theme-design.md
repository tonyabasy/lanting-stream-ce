# 主题配置设计

## 概述

主题系统完全基于 antd 6 原生能力，零抽象层。

- 通过 `cssVar: true` 注入 `--ant-xxx` CSS 变量
- `theme.useToken()` 获取 Token 供 TSX 组件使用
- 主题配置集中在 `hooks/useThemeMode.ts`

---

## 目录结构

```
src/
├── hooks/
│   └── useThemeMode.ts            ← 主题配置 + Light/Dark 切换
├── app.tsx                        ← rootContainer 包裹全局 ConfigProvider
└── pages/dev/index.css            ← 使用 --ant-xxx CSS 变量
```

---

## 数据流

```
useThemeMode()
    │
    └── themeConfig { cssVar: true, algorithm, token: { colorPrimary } }
            │
            ├── ConfigProvider (rootContainer)
            │     ├── antd 自动注入 --ant-xxx 到 :root
            │     └── 所有 antd 组件自动跟随主题
            │
            └── theme.useToken() → TSX 组件获取 token
```

---

## 主题切换

```ts
const { setMode } = useThemeMode();
setMode('dark'); // 全局 CSS 变量自动切换，零重渲染
```

---

## 约束

- ✗ 禁止 hardcode 颜色/间距/圆角值
- ✗ 禁止使用 `useModel('theme')`
- ✓ TSX 组件使用 `const { token } = theme.useToken()`
- ✓ CSS 文件使用 `var(--ant-xxx)` 变量
