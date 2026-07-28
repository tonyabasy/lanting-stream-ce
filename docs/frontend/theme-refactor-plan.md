# 主题系统重构 — 实施计划

> 基于 `docs/frontend/theme-refactor-design.md` v3 零抽象方案

---

## 总览

| 操作 | 文件数 |
|---|---|
| 新建 | 1 |
| 修改（TSX） | 12 |
| 修改（CSS） | 1（47 处替换） |
| 删除 | 3 |
| **合计** | **17** |

---

## 一、新建文件

### 1.1 `web/src/hooks/useThemeMode.ts`

40 行。唯一的状态逻辑：存储当前主题模式（`'light' | 'dark'`），导出 `themeConfig`。

```ts
// 伪代码
light: { cssVar: true, algorithm: theme.defaultAlgorithm, token: { colorPrimary: '#4A8A6A' } }
dark:  { cssVar: true, algorithm: theme.darkAlgorithm,  token: { colorPrimary: '#5AAA7A' } }
```

暴露 `{ mode, themeConfig, setMode }`。

---

## 二、修改文件（TSX）

所有 TSX 文件的改动模式相同：
1. 删除 `import type { LantingToken } from '@/themes'`
2. 删除 `import { toAntdTheme } from '@/themes'`（如果有）
3. `useModel('theme')` → `theme.useToken()`
4. `LantingToken` 类型引用 → 删除或改为 antd 的 `GlobalToken`
5. 如有 `<ConfigProvider theme={toAntdTheme(token)}>` → 删除（root 已统一注入）
6. 添加 `import { theme } from 'antd'`（如果未导入）

### 2.1 `web/src/app.tsx`

**改动：** 新增 `rootContainer` 导出，包裹全局 ConfigProvider。

```tsx
// 新增
import { ConfigProvider } from 'antd';
import { useThemeMode } from '@/hooks/useThemeMode';

export function rootContainer(container: React.ReactNode) {
  return <ThemeWrapper>{container}</ThemeWrapper>;
}

function ThemeWrapper({ children }: { children: React.ReactNode }) {
  const { themeConfig } = useThemeMode();
  return <ConfigProvider theme={themeConfig}>{children}</ConfigProvider>;
}
```

**影响范围：** 全局，所有路由和页面都被包裹。

### 2.2 `web/src/layouts/index.tsx`

| 行 | 改动 |
|---|---|
| L15 | 删除 `import type { LantingToken } from '@/themes'` |
| L16 | 删除 `import { toAntdTheme } from '@/themes'` |
| L2 | `ConfigProvider` 从 antd import 中移除（rootContainer 已提供） |
| L59 | `const token = useModel('theme') as LantingToken` → `const { token } = theme.useToken()` |
| L66 | 删除外层 `<ConfigProvider theme={toAntdTheme(token)}>` 标签及对应 `</ConfigProvider>` |
| - | `token.sizeLG` / `token.sizeMD` / `token.sizeSM` / `token.sizeXL` / `token.size2XL` / `token.sizeXS` 等 → 改名为 antd 对应 token（见 CSS 映射表） |
| L117 | `LanguageSwitch` 不再需要传 `token` prop → `<LanguageSwitch />` |

### 2.3 `web/src/pages/login.tsx`

| 行 | 改动 |
|---|---|
| L6 | 删除 `import { toAntdTheme } from '@/themes'` |
| L7 | 删除 `import type { LantingToken } from '@/themes'` |
| L3 | `ConfigProvider` 从 antd import 中移除 |
| L16 | `const token = useModel('theme') as LantingToken` → `const { token } = theme.useToken()` |
| - | 删除 `<ConfigProvider theme={toAntdTheme(token)}>` 及对应闭合标签 |

### 2.4 `web/src/pages/dev/index.tsx`

| 行 | 改动 |
|---|---|
| L5 | 删除 `import { toAntdTheme } from '@/themes'` |
| L6 | 删除 `import type { LantingToken } from '@/themes'` |
| L3 | `ConfigProvider` 从 antd import 中移除 |
| L18 | `const token = useModel('theme') as LantingToken` → `const { token } = theme.useToken()` |
| L22 | 删除 `<ConfigProvider theme={toAntdTheme(token)}>` 及对应闭合标签 |

### 2.5 `web/src/pages/dev/panels/TopBar.tsx`

| 行 | 改动 |
|---|---|
| L3 | 删除 `import type { LantingToken } from '@/themes'` |
| L6 | `const t = useModel('theme') as LantingToken` → `const { token: t } = theme.useToken()` |
| L4 | 删除 `useModel` from umi import（如果只有这一个用途） |

### 2.6 `web/src/pages/cluster/index.tsx`

| 行 | 改动 |
|---|---|
| L21 | 删除 `import type { LantingToken } from '@/themes'` |
| L52 | `const token = useModel('theme') as LantingToken` → `const { token } = theme.useToken()` |
| L290 | `ClusterCardProps` 中删除 `token: LantingToken` 属性 |
| L296 | `ClusterCard` 组件内部改为 `const { token } = theme.useToken()` |
| L374 | `actionBtnStyle` 函数参数从 `token: LantingToken` 改为 `token: GlobalToken` |

### 2.7 `web/src/pages/ops/index.tsx`

| 行 | 改动 |
|---|---|
| L11 | 删除 `import type { LantingToken } from '@/themes'` |
| L36 | `const token = useModel('theme') as LantingToken` → `const { token } = theme.useToken()` |

### 2.8 `web/src/pages/design/cluster.tsx`

| 行 | 改动 |
|---|---|
| L3 | 删除 `import type { LantingToken } from '@/themes'` |
| L6 | `const token = useModel('theme') as LantingToken` → `const { token } = theme.useToken()` |

### 2.9 `web/src/pages/design/editor.tsx`

| 行 | 改动 |
|---|---|
| L3 | 删除 `import type { LantingToken } from '@/themes'` |
| L6 | `const token = useModel('theme') as LantingToken` → `const { token } = theme.useToken()` |

### 2.10 `web/src/pages/design/login.tsx`

| 行 | 改动 |
|---|---|
| L3 | 删除 `import type { LantingToken } from '@/themes'` |
| L6 | `const token = useModel('theme') as LantingToken` → `const { token } = theme.useToken()` |

### 2.11 `web/src/pages/design/theme-preview.tsx`

| 行 | 改动 |
|---|---|
| L3 | 删除 `import type { LantingToken } from '@/themes'` |
| L6 | `const token = useModel('theme') as LantingToken` → `const { token } = theme.useToken()` |
| - | 所有 `token.xxx` 引用 → 名称不变（antd token 字段名和旧 LantingToken 大部分一致） |
| - | 需特殊处理的：`token.colorPrimaryHover` ✅ 同名；`token.colorPrimaryBorder` ✅ 同名；`token.colorTextDescription` → `token.colorTextTertiary` 或保留 `colorTextDescription`（antd 有这个 alias）；`token.colorBgSubtle` → `token.colorFillQuaternary`；`token.colorBgMuted` → `token.colorFillSecondary`；`token.colorBgActive` → `token.colorFill`；`token.colorSeparator` → `token.colorSplit` |
| - | 间距：`token.sizeXS` 等 → antd 命名偏移一位（见 CSS 映射表） |
| - | 字号：`token.fontSizeCaption`(12) → `token.fontSizeSM`(12)；`token.fontSizeBody`(14) → `token.fontSize`(14)；`token.fontSizeTitle`(16) → `token.fontSizeLG`(16)；`token.fontSizeHeading`(20) → `token.fontSizeXL`(20) |
| - | 字重：`token.fontWeightRegular` → 直接用 `400`；`token.fontWeightMedium` → `500` |
| - | 圆角：`token.borderRadiusMD`(8) → `token.borderRadiusLG`；`token.borderRadiusXL`(16) → `token.borderRadiusLG`（或删除该档位） |
| - | 阴影：`token.boxShadowCard` → `token.boxShadowTertiary` |

### 2.12 `web/src/components/LanguageSwitch.tsx`

| 行 | 改动 |
|---|---|
| L3 | 删除 `import type { LantingToken } from '@/themes'` |
| L5-L6 | 删除 `token: LantingToken` prop 类型 |
| L14 | 改为 `const { token } = theme.useToken()`，不再接收 props |
| - | `token.sizeXS` → `token.sizeXXS`；`token.sizeSM` → `token.sizeXS`；`token.fontSizeCaption` → `token.fontSizeSM`；`token.borderRadius` → 同名保留 |

---

## 三、修改文件（CSS）

### 3.1 `web/src/pages/dev/index.css`

47 处 `var(--lt-xxx)` → `var(--ant-xxx)`，完整映射表：

| 旧变量 | 新变量 | 次数 |
|---|---|---|
| `--lt-color-bg-container` | `--ant-color-bg-container` | 1 |
| `--lt-radius-md` | `--ant-border-radius-lg` | 1 |
| `--lt-size-sm` | `--ant-size-xs` | 2 |
| `--lt-size-xs` | `--ant-size-xxs` | 6 |
| `--lt-color-text-description` | `--ant-color-text-description` | 15 |
| `--lt-font-weight-medium` | `500` | 1 |
| `--lt-radius-sm` | `--ant-border-radius-sm` | 3 |
| `--lt-color-bg-muted` | `--ant-color-fill-secondary` | 2 |
| `--lt-color-text-secondary` | `--ant-color-text-secondary` | 6 |
| `--lt-color-bg-subtle` | `--ant-color-fill-quaternary` | 1 |
| `--lt-color-separator` | `--ant-color-split` | 2 |
| `--lt-color-bg-active` | `--ant-color-fill` | 2 |
| `--lt-font-size-caption` | `--ant-font-size-sm` | 1 |
| `--lt-color-primary` | `--ant-color-primary` | 1 |
| `--lt-color-bg-hover` | `--ant-color-bg-text-hover` | 1 |
| `--lt-color-warning-bg` | `--ant-color-warning-bg` | 2 |

---

## 四、删除文件

| 文件 | 说明 |
|---|---|
| `web/src/themes/theme-default-light.json` | 不再需要 JSON 文件 |
| `web/src/themes/index.ts` | 不再需要 LantingToken / injectCSSVars / toAntdTheme |
| `web/src/models/theme.ts` | 不再需要 model，由 useThemeMode hook 替代 |

---

## 五、更新文档

| 文件 | 说明 |
|---|---|
| `docs/frontend/theme-design.md` | 更新为 v3 架构描述 |

---

## 六、风险项

| 风险 | 缓解 |
|---|---|
| antd `cssVar: true` 在 antd 6.5 中的行为需要验证 | 改造完成后在浏览器中检查 `:root` 是否注入了 `--ant-xxx` 变量 |
| `token.colorTextDescription` / `token.colorTextTertiary` — antd 中这是 AliasToken，别名于 `colorTextTertiary` | `theme.useToken()` 返回的 token 同时包含两个字段，优先用 `colorTextDescription`（语义更明确） |
| `token.colorBgSubtle/Muted/Active` 改为 `colorFill*` 后视觉可能微调 | 预览页逐项对比 |
| 间距命名偏移（`sizeXS` → `sizeXXS`）容易出错 | 按映射表逐行替换，回顾时重点检查 |
| `rootContainer` 中使用了 React hook，需要确保不会在 SSR 场景出问题 | 当前 CSR 场景无影响 |
| 删除 `models/theme.ts` 后其他模块可能有隐式依赖 | 已全量扫描，`models/theme` 无其他引用 |
