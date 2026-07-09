# 主题配置设计方案

## 概述

主题系统分为三层：数据层（JSON）、解析层（index.ts）、应用层（ConfigProvider + useModel）。后端可下发主题配置覆盖前端默认值，前端始终有兜底。

---

## 目录结构

```
src/
├── themes/
│   ├── theme-default-light.json   ← 默认主题数据（打包进 bundle）
│   └── index.ts              ← 类型定义 + 解析函数
├── models/
│   └── useTheme.ts                ← 合并远程主题与默认值
└── layouts/
    ├── index.tsx                  ← 主 Layout，含 ConfigProvider
    └── LoginLayout.tsx            ← 登录页 Layout，静态主题
```

---

## 数据流

```
theme-default-light.json（前端默认，打包进 bundle）
        ↓ flattenTheme()
    DEFAULT_TOKEN（模块加载时解析一次）
        ↓
getInitialState() → fetch /api/theme/xxx（登录后获取）
        ↓ 存入 initialState.themeConfig
useTheme() → merge(DEFAULT_TOKEN, flattenTheme(remote))
        ↓ 两条线并行
┌───────────────────────┬────────────────────────┐
│ useModel('theme')     │ toAntdTheme(token)      │
│ 组件取自定义 token     │ ConfigProvider          │
│ colorBgSubtle 等      │ antd 组件跟着主题变      │
└───────────────────────┴────────────────────────┘
```

---

## 一、数据层：theme-default-light.json

嵌套结构，按语义分组，便于后端按需下发部分覆盖。

```json
{
  "key": "default-light",
  "name": "宝蓝-light",
  "brand": {
    "colorPrimary": "#2A5CA0",
    "colorPrimaryHover": "#234B85",
    "colorPrimaryBg": "#2A5CA014",
    "colorPrimaryBorder": "#6089C4",
    "colorLink": "#2A5CA0",
    "colorLinkHover": "#234B85"
  },
  "background": { ... },
  "border": { ... },
  "text": { ... },
  "semantic": { ... },
  "font": { ... },
  "fontSize": { ... },
  "fontWeight": { ... },
  "borderRadius": { ... },
  "spacing": { ... },
  "boxShadow": { ... }
}
```

**为什么用 JSON 而不是 TS 文件：**
- 后端可以返回相同结构，前后端统一格式
- 方便未来从 CDN 或配置中心下发
- 打包进 bundle，不暴露在 public 目录

---

## 二、解析层：index.ts

类型定义和解析函数放同一文件，因为强耦合，没有拆分必要。

### LantingToken 接口

扁平结构，包含 antd 原生字段和自定义扩展字段：

```ts
export interface LantingToken {
  // 品牌色
  colorPrimary: string;
  colorPrimaryHover: string;
  colorPrimaryBg: string;
  colorPrimaryBorder: string;

  // 背景（colorBgSubtle / colorBgMuted 是自定义扩展，antd 没有）
  colorBgLayout: string;
  colorBgContainer: string;
  colorBgElevated: string;
  colorBgSubtle: string;
  colorBgMuted: string;

  // 文字
  colorText: string;
  colorTextSecondary: string;
  colorTextDescription: string;
  colorTextDisabled: string;
  colorNeutral: string;

  // 语义色
  colorSuccess: string;
  colorSuccessBg: string;
  colorWarning: string;
  colorWarningBg: string;
  colorError: string;

  // 字体
  fontFamily: string;
  fontFamilyCode: string;

  // 字号（自定义扩展）
  fontSizeCaption: number;   // 12
  fontSizeBody: number;      // 14
  fontSizeTitle: number;     // 16
  fontSizeHeading: number;   // 20

  // 字重（自定义扩展）
  fontWeightRegular: number; // 400
  fontWeightMedium: number;  // 500

  // 圆角
  borderRadiusSM: number;    // 4
  borderRadius: number;      // 6
  borderRadiusLG: number;    // 12

  // 间距（自定义扩展）
  spacingXS: number;   // 4
  spacingSM: number;   // 8
  spacingMD: number;   // 12
  spacingLG: number;   // 16
  spacingXL: number;   // 20
  spacing2XL: number;  // 24
  spacing3XL: number;  // 32
  spacing4XL: number;  // 48

  // 阴影
  boxShadow: string;
  boxShadowSecondary: string;
}
```

### flattenTheme()

嵌套 JSON → 扁平 LantingToken：

```ts
export function flattenTheme(raw: RawTheme): LantingToken {
  return {
    ...raw.brand,
    ...raw.background,
    ...raw.border,
    ...raw.text,
    ...raw.semantic,
    ...raw.font,
    ...raw.fontSize,
    ...raw.fontWeight,
    ...raw.borderRadius,
    ...raw.spacing,
    ...raw.boxShadow,
  } as LantingToken;
}
```

### toAntdTheme()

LantingToken → antd ThemeConfig，只传 antd 认识的字段：

```ts
export function toAntdTheme(t: LantingToken): ThemeConfig {
  return {
    token: {
      colorPrimary:         t.colorPrimary,
      colorSuccess:         t.colorSuccess,
      colorWarning:         t.colorWarning,
      colorError:           t.colorError,
      colorBgLayout:        t.colorBgLayout,
      colorBgContainer:     t.colorBgContainer,
      colorBgElevated:      t.colorBgElevated,
      colorText:            t.colorText,
      colorTextSecondary:   t.colorTextSecondary,
      colorTextDescription: t.colorTextDescription,
      colorTextDisabled:    t.colorTextDisabled,
      colorBorder:          t.colorBorder,
      colorBorderSecondary: t.colorBorderSecondary,
      fontFamily:           t.fontFamily,
      fontFamilyCode:       t.fontFamilyCode,
      fontSize:             t.fontSizeBody,
      borderRadius:         t.borderRadius,
      borderRadiusSM:       t.borderRadiusSM,
      borderRadiusLG:       t.borderRadiusLG,
      boxShadow:            t.boxShadow,
      boxShadowSecondary:   t.boxShadowSecondary,
    },
    components: {
      Button: { primaryShadow: 'none', defaultShadow: 'none' },
      Input:  { activeBorderColor: t.colorPrimary, hoverBorderColor: t.colorBorderSecondary },
      Table:  { headerBg: t.colorBgSubtle, rowHoverBg: t.colorPrimaryBg, borderColor: t.colorBorder },
      Menu:   { itemSelectedBg: t.colorPrimaryBg, itemSelectedColor: t.colorPrimary, itemHoverBg: t.colorBgSubtle },
      Modal:  { borderRadiusLG: t.borderRadiusLG },
    },
  };
}
```

---

## 三、应用层

### models/useTheme.ts

```ts
import { useModel } from 'umi';
import defaultRaw from '@/themes/theme-default-light.json';
import { flattenTheme, type LantingToken, type RawTheme } from '@/themes';

const DEFAULT_TOKEN: LantingToken = flattenTheme(defaultRaw);

export default function useTheme(): LantingToken {
  const { initialState } = useModel('@@initialState');
  const remote = initialState?.themeConfig as RawTheme | undefined;
  if (remote) {
    return { ...DEFAULT_TOKEN, ...flattenTheme(remote) };
  }
  return DEFAULT_TOKEN;
}
```

### app.tsx — getInitialState

主题 fetch 失败不阻塞登录，降级到默认主题：

```ts
export async function getInitialState() {
  const token = getToken();
  if (!token) return { currentUser: null, themeConfig: null };

  const themeConfig = await fetch('/api/theme/default-light')
    .then(r => r.json())
    .catch(() => null);  // 失败降级，不抛错

  try {
    const { getCurrentUser } = await import('@/services/auth');
    const user = await getCurrentUser();
    return { currentUser: user, themeConfig };
  } catch {
    removeToken();
    return { currentUser: null, themeConfig };
  }
}
```

### layouts/index.tsx — 主 Layout

登录后所有页面共用，ConfigProvider 在此注入：

```tsx
const AppLayout: React.FC = () => {
  const token = useModel('theme');
  return (
    <ConfigProvider theme={toAntdTheme(token)}>
      {/* 顶栏 + 侧边栏 + Outlet */}
    </ConfigProvider>
  );
};
```

### layouts/LoginLayout.tsx — 登录页 Layout

用静态默认主题，不依赖 useModel（此时 Model Provider 尚未就绪）：

```tsx
import { toAntdTheme, flattenTheme } from '@/themes';
import defaultRaw from '@/themes/theme-default-light.json';

const staticTheme = toAntdTheme(flattenTheme(defaultRaw));

const LoginLayout: React.FC = () => (
  <ConfigProvider theme={staticTheme}>
    <Outlet />
  </ConfigProvider>
);
```

路由配置：

```ts
{ path: '/login', component: '@/layouts/LoginLayout', routes: [
  { path: '/login', component: 'login' }
]}
```

---

## 四、组件使用规范

```tsx
// 所有 token 从 useModel('theme') 取，禁止 hardcode
const token = useModel('theme');

<div style={{
  background:   token.colorBgContainer,
  color:        token.colorText,
  fontSize:     token.fontSizeBody,
  padding:      `${token.spacingMD}px ${token.spacingLG}px`,
  borderRadius: token.borderRadius,
  border:       `0.5px solid ${token.colorBorder}`,
}}>
```

---

## 五、约束与禁止事项

```
✗ 组件里写裸 hex：color: '#2A5CA0'
✗ 组件里写裸数值：fontSize: 14, padding: '12px 16px'
✗ 使用 theme.useToken()，统一用 useModel('theme')
✗ 在 rootContainer 里使用 useModel（Model Provider 尚未就绪）
✗ 主题文件放在 public/ 目录（会被直接暴露）
```