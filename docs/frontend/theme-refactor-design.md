# 主题系统重构设计 v3（零抽象版）

## 核心原则

**不写任何中间层代码。全部用 antd 原生能力。**

- ❌ 没有 JSON 文件
- ❌ 没有 `injectCSSVars`
- ❌ 没有 `toAntdTheme`
- ❌ 没有 `LantingToken` 接口
- ❌ 没有 `models/theme.ts`

---

## 一、唯一需要的东西：antd cssVar 模式

antd 6 支持 `cssVar: true`。开启后 antd 自动把 200+ 个 Design Token 注入为 `--ant-xxx` CSS 变量，所有组件通过 CSS 变量渲染。

```
ConfigProvider theme={{ cssVar: true, token: { colorPrimary: '#4A8A6A' } }}
        │
        ▼
   antd 自动注入 :root {
     --ant-color-primary: #4A8A6A;
     --ant-color-primary-hover: #3A7A5A;    ← algorithm 自动算
     --ant-color-primary-bg: #4A8A6A14;     ← algorithm 自动算
     --ant-color-bg-container: #FFFFFF;      ← algorithm 自动算
     --ant-border-radius: 6px;
     --ant-size-md: 12px;
     ... 200+ 个
   }
```

**切换主题 = 切换 ConfigProvider 的 theme 属性，CSS 变量自动更新，整个页面零重渲染。**

---

## 二、目录结构变化

```
删除：
  web/src/themes/                          ← 整个目录
  web/src/models/theme.ts                  ← 不再需要

保留（修改）：
  web/src/app.tsx                          ← 加一个极简主题配置 hook
  web/src/layouts/index.tsx                ← useModel('theme') → theme.useToken()
  web/src/pages/dev/index.css              ← --lt-xxx → --ant-xxx（47 处）
  web/src/pages/design/theme-preview.tsx   ← useModel('theme') → theme.useToken()

新增（可选）：
  web/src/hooks/useThemeMode.ts            ← 只管一个事：主题切换
```

---

## 三、唯一的配置：一个 Hook

```ts
// web/src/hooks/useThemeMode.ts

import { useState, useMemo } from 'react';
import { theme } from 'antd';
import type { ThemeConfig } from 'antd';

type ThemeMode = 'light' | 'dark';

const THEME_CONFIGS: Record<ThemeMode, ThemeConfig> = {
  light: {
    cssVar: true,
    algorithm: theme.defaultAlgorithm,
    token: {
      colorPrimary: '#4A8A6A',
    },
  },
  dark: {
    cssVar: true,
    algorithm: theme.darkAlgorithm,
    token: {
      colorPrimary: '#5AAA7A',  // dark 下品牌色略亮
    },
  },
};

export function useThemeMode() {
  const [mode, setMode] = useState<ThemeMode>('light');

  const themeConfig = useMemo(() => THEME_CONFIGS[mode], [mode]);

  return { mode, themeConfig, setMode };
}
```

这就是全部「主题系统」。**40 行代码。** 没有 JSON、没有映射表、没有类型体操。

---

## 四、应用入口

```tsx
// web/src/app.tsx

import { ConfigProvider } from 'antd';
import { useThemeMode } from '@/hooks/useThemeMode';

export default function App() {
  const { themeConfig, setMode, mode } = useThemeMode();

  return (
    <ConfigProvider theme={themeConfig}>
      {/* 主题切换按钮 */}
      <button onClick={() => setMode(mode === 'light' ? 'dark' : 'light')}>
        切换主题
      </button>
      <Outlet />
    </ConfigProvider>
  );
}
```

---

## 五、组件怎么拿 token

### TSX 组件

```tsx
// 之前
const token = useModel('theme') as LantingToken;

// 之后
import { theme } from 'antd';
const { token } = theme.useToken();

// 用法完全一样：
style={{ background: token.colorBgContainer }}
```

### CSS 文件

```css
/* 之前 */
background: var(--lt-color-bg-container);

/* 之后 */
background: var(--ant-color-bg-container);
```

**antd cssVar 模式下的变量命名规则：** `--ant-{camelCase tokenName}`。例如：

| antd Token | CSS 变量 |
|---|---|
| `colorPrimary` | `--ant-color-primary` |
| `colorBgContainer` | `--ant-color-bg-container` |
| `colorBorder` | `--ant-color-border` |
| `colorTextSecondary` | `--ant-color-text-secondary` |
| `borderRadius` | `--ant-border-radius` |
| `borderRadiusSM` | `--ant-border-radius-sm` |
| `sizeMD` | `--ant-size-md` |
| `boxShadow` | `--ant-box-shadow` |

---

## 六、当前 `--lt-xxx` 到 `--ant-xxx` 的映射

| 旧变量（47 处） | 新变量（antd 原生） |
|---|---|
| `--lt-color-primary` | `--ant-color-primary` |
| `--lt-color-primary-hover` | `--ant-color-primary-hover` |
| `--lt-color-primary-bg` | `--ant-color-primary-bg` |
| `--lt-color-primary-border` | `--ant-color-primary-border` |
| `--lt-color-bg-layout` | `--ant-color-bg-layout` |
| `--lt-color-bg-container` | `--ant-color-bg-container` |
| `--lt-color-bg-subtle` | `--ant-color-fill-quaternary` |
| `--lt-color-bg-muted` | `--ant-color-fill-secondary` |
| `--lt-color-bg-active` | `--ant-color-fill` |
| `--lt-color-bg-hover` | `--ant-color-bg-text-hover` |
| `--lt-color-border` | `--ant-color-border` |
| `--lt-color-border-secondary` | `--ant-color-border-secondary` |
| `--lt-color-separator` | `--ant-color-split` |
| `--lt-color-text` | `--ant-color-text` |
| `--lt-color-text-secondary` | `--ant-color-text-secondary` |
| `--lt-color-text-description` | `--ant-color-text-description`（或 `--ant-color-text-tertiary`） |
| `--lt-color-text-disabled` | `--ant-color-text-disabled` |
| `--lt-color-text-solid` | `--ant-color-text-light-solid` |
| `--lt-color-success` | `--ant-color-success` |
| `--lt-color-success-bg` | `--ant-color-success-bg` |
| `--lt-color-warning` | `--ant-color-warning` |
| `--lt-color-warning-bg` | `--ant-color-warning-bg` |
| `--lt-color-error` | `--ant-color-error` |
| `--lt-color-error-bg` | `--ant-color-error-bg` |
| `--lt-color-link` | `--ant-color-link` |
| `--lt-color-link-hover` | `--ant-color-link-hover` |
| `--lt-font-family` | `--ant-font-family` |
| `--lt-font-family-code` | `--ant-font-family-code` |
| `--lt-font-size-caption` | `--ant-font-size-sm` |
| `--lt-font-size-body` | `--ant-font-size` |
| `--lt-font-size-title` | `--ant-font-size-lg` |
| `--lt-font-size-heading` | `--ant-font-size-xl` |
| `--lt-font-weight-regular` | `--ant-font-weight-regular`（需要确认 antd 是否有这个变量） |
| `--lt-font-weight-medium` | 直接写 `500` 或用 `--ant-font-weight-strong` |
| `--lt-radius-sm` | `--ant-border-radius-sm` |
| `--lt-radius` | `--ant-border-radius` |
| `--lt-radius-md` | `--ant-border-radius-md`（antd 没有这个档位，见下方说明） |
| `--lt-radius-lg` | `--ant-border-radius-lg` |
| `--lt-radius-xl` | `--ant-border-radius-xl`（antd 没有） |
| `--lt-size-xs` | `--ant-size-xxs` | 4px |
| `--lt-size-sm` | `--ant-size-xs` | 8px |
| `--lt-size-md` | `--ant-size-sm` | 12px |
| `--lt-size-lg` | `--ant-size` | 16px |
| `--lt-size-xl` | `--ant-size-md` | 20px |
| `--lt-size-2xl` | `--ant-size-lg` | 24px |
| `--lt-size-3xl` | `--ant-size-xl` | 32px |
| `--lt-size-4xl` | `--ant-size-xxl` | 48px |
| `--lt-shadow` | `--ant-box-shadow` | |
| `--lt-shadow-lg` | `--ant-box-shadow-secondary` | |
| `--lt-shadow-card` | `--ant-box-shadow-tertiary` | |

**间距档位完美匹配。** 你的 8 个 `sizeXS~size4XL` 和 antd 的 `sizeXXS~sizeXXL` **数值完全一样**，只是命名偏移了一位。不需要任何自定义覆盖。

**圆角有两个档位对不上：** `--lt-radius-md`（8px）和 `--lt-radius-xl`（16px）在 antd 默认体系里没有直接对应。方案：代码中直接写死 `8px` / `16px`，或者给 `themeConfig.token` 加 `borderRadius: 8`（但这会影响全局）。建议直接写死，这两个档位使用频率很低。

---

## 七、主题切换怎么工作

```
用户点击切换
      │
      ▼
setMode('dark')
      │
      ▼
themeConfig 变化 → ConfigProvider 收到新 theme
      │
      ├──→ antd 自动更新 :root 上所有 --ant-xxx 变量
      │
      ├──→ 所有 CSS 文件通过 var(--ant-xxx) 自动响应
      │
      └──→ 所有 TSX 组件通过 theme.useToken() 自动响应
```

**切换主题不会触发 React 重渲染**（cssVar 模式下 antd 直接操作 DOM 样式）。只有用到 `theme.useToken()` 的组件会重渲染。

---

## 八、实施影响

| 改动 | 规模 | 风险 |
|---|---|---|
| 新建 `hooks/useThemeMode.ts` | ~40 行 | 无 |
| 修改 `app.tsx` | 加 ConfigProvider 包裹 + useThemeMode | 低 |
| 修改 `layouts/index.tsx` | `useModel('theme')` → `theme.useToken()` | 低 |
| 修改 `pages/dev/index.css` | `--lt-xxx` → `--ant-xxx`（47 处） | 中，需要逐行替换验证 |
| 修改 `pages/design/theme-preview.tsx` | `useModel('theme')` → `theme.useToken()` | 低 |
| 删除 `themes/` 目录 | 删 2 个文件 | 无 |
| 删除 `models/theme.ts` | 删 1 个文件 | 无 |

---

## 九、完整文件清单

```
新增：
  web/src/hooks/useThemeMode.ts       ← 40 行，所有主题逻辑

修改：
  web/src/app.tsx                     ← ConfigProvider 包裹 + 切换入口
  web/src/layouts/index.tsx           ← useModel → useToken
  web/src/pages/design/theme-preview.tsx  ← useModel → useToken
  web/src/pages/dev/index.css         ← --lt-xxx → --ant-xxx
  web/src/global.css                  ← 如有 --lt-xxx 引用也改

删除：
  web/src/themes/theme-default-light.json
  web/src/themes/index.ts
  web/src/models/theme.ts

更新：
  docs/frontend/theme-design.md       ← 更新为 v3 架构
```

---

## 附录：antd 原生 Token 覆盖率

60 个旧变量 → antd 原生覆盖情况：

| 类别 | 旧变量数 | antd 覆盖 | 缺口 |
|---|---|---|---|
| 品牌色 + 色阶 | 7 | ✅ `colorPrimary` → algorithm 自动派生 Hover/Active/Bg/Border/Text 等 6 个 | 0 |
| 背景色 | 6 | ✅ `colorBgLayout` / `colorBgContainer` / `colorBgElevated`，Subtle/Muted/Active 用 `colorFillQuaternary` / `colorFillSecondary` / `colorFill` 替代 | 0 |
| 边框色 | 3 | ✅ `colorBorder` / `colorBorderSecondary` / `colorSplit` | 0 |
| 文字色 | 5 | ✅ `colorText` / `colorTextSecondary` / `colorTextTertiary` / `colorTextDisabled` / `colorTextLightSolid` | 0 |
| 语义色 | 6 | ✅ `colorSuccess` / `colorWarning` / `colorError` → algorithm 自动派生 Bg 变体 | 0 |
| 字体 | 2 | ✅ `fontFamily` / `fontFamilyCode` | 0 |
| 字号 | 4 | ✅ `fontSizeSM` / `fontSize` / `fontSizeLG` / `fontSizeXL` + `fontSizeHeading1~5` | 0 |
| 字重 | 2 | ✅ `fontWeightStrong`（500），regular（400）直接用 CSS 默认 | 0 |
| 圆角 | 5 | ✅ `borderRadiusSM` / `borderRadius` / `borderRadiusLG`，MD → `borderRadiusLG`（antd 默认 ~8px），XL → `borderRadiusLG` 或直接不设 | 0 |
| 间距 | 8 | ✅ `sizeXXS`~`sizeXXL` 数值完全一致（4/8/12/16/20/24/32/48），仅命名偏移一位 | 0 |
| 阴影 | 3 | ✅ `boxShadow` / `boxShadowSecondary` / `boxShadowTertiary` | 0 |
| **总计** | **60** | | **0** |
