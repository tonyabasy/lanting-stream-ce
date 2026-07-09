# 前端 CSS 规范文档

## 核心原则

> 所有样式值从 `useModel('theme')` 取，禁止在组件里 hardcode 任何颜色、字号、尺寸、圆角值。

---

## 一、Token 使用方式

```tsx
const token = useModel('theme');

<div style={{
  fontSize: token.fontSizeBody,
  padding: `${token.sizeMD}px ${token.sizeLG}px`,
  color: token.colorText,
  borderRadius: token.borderRadius,
}}>
```

---

## 二、LantingToken 完整接口定义

参考 `src/themes/index.ts`

---

## 三、颜色规范

### 品牌色

| Token | 用途 |
|-------|------|
| `colorPrimary` | 主色，按钮、选中状态 |
| `colorPrimaryHover` | 按钮 hover 状态 |
| `colorPrimaryActive` | 按钮 active 状态 |
| `colorPrimaryBg` | 选中行、激活导航底色 |
| `colorPrimaryBorder` | 输入框 focus 边框 |
| `colorLink` | 链接文字 |
| `colorLinkHover` | 链接 hover |

### 背景层级

| Token | 用途 |
|-------|------|
| `colorBgLayout` | 最底层页面背景 |
| `colorBgContainer` | 卡片、面板、顶栏 |
| `colorBgElevated` | 弹窗、下拉菜单 |
| `colorBgSubtle` | 侧边栏、stat 块、代码行号区 |
| `colorBgMuted` | 更深一层的背景，分隔块 |

### 文字层级

| Token | 用途 |
|-------|------|
| `colorText` | 主文字，标题、正文、作业名 |
| `colorTextSecondary` | 次要文字，集群名、时间 |
| `colorTextDescription` | 提示文字，placeholder、副标题 |
| `colorTextDisabled` | 禁用文字、行号 |
| `colorNeutral` | 中性状态，如已停止的作业 |

### 边框

| Token | 用途 |
|-------|------|
| `colorBorder` | 卡片、输入框、分隔线 |
| `colorBorderSecondary` | hover 状态、强调边框 |
| `colorSeparator` | 细分隔线（半透明） |

### 语义色

| Token | 用途 |
|-------|------|
| `colorSuccess` / `colorSuccessBg` | 运行中、操作成功 |
| `colorWarning` / `colorWarningBg` | 反压、注意 |
| `colorError` / `colorErrorBg` | 异常、错误 |

---

## 四、字号规范

| Token | 值 | 用途 |
|-------|-----|------|
| `fontSizeCaption` | 12 | badge、行号、辅助文字、状态栏 |
| `fontSizeBody` | 14 | 正文、表格内容、菜单项 |
| `fontSizeTitle` | 16 | 卡片标题、分组标题 |
| `fontSizeHeading` | 20 | 页面标题 |

### 字重

| Token | 值 | 用途 |
|-------|-----|------|
| `fontWeightRegular` | 400 | 正文、次要信息 |
| `fontWeightMedium` | 500 | 标题、按钮文字、强调 |

**禁止使用 600 / 700 / bold。**

### 字体栈

| Token | 用途 |
|-------|------|
| `fontFamily` | 所有 UI 文字（sans） |
| `fontFamilyCode` | 代码区、数值、行号 |

Serif 字体（Playfair Display + 思源宋体）不走 token，通过 CSS 变量 `var(--font-serif)` 在 global.css 中定义，**限用两处**：Logo 品牌名、AI 生成内容正文。

---

## 五、圆角规范

| Token | 值 | 用途 |
|-------|-----|------|
| `borderRadiusSM` | 4 | Tag、Badge |
| `borderRadius` | 6 | 按钮、输入框、菜单项 |
| `borderRadiusMD` | 8 | 中等卡片、下拉菜单 |
| `borderRadiusLG` | 12 | Modal、大卡片 |
| `borderRadiusXL` | 16 | 大型容器、特殊面板 |

---

## 六、尺寸规范

尺寸只从以下八个档位取值，禁止使用其他数值：

| Token | 值 | 典型用途 |
|-------|-----|---------|
| `sizeXS` | 4 | 图标与文字尺寸、紧凑行内尺寸 |
| `sizeSM` | 8 | 按钮内边距（垂直）、列表行尺寸 |
| `sizeMD` | 12 | 卡片内边距（小）、表单项尺寸 |
| `sizeLG` | 16 | 卡片内边距（标准）、section 尺寸 |
| `sizeXL` | 20 | 页面内容区 padding（垂直） |
| `size2XL` | 24 | 页面内容区 padding（水平） |
| `size3XL` | 32 | 页面区块尺寸 |
| `size4XL` | 48 | 页面顶部留白 |

---

## 七、阴影规范

| Token | 用途 |
|-------|------|
| `boxShadow` | 卡片、下拉菜单 |
| `boxShadowSecondary` | Modal、抽屉 |

---

## 八、禁止事项

```
✗ color: '#2A5CA0'              → 用 token.colorPrimary
✗ fontSize: 14                  → 用 token.fontSizeBody
✗ padding: '12px 16px'          → 用 token.sizeMD / token.sizeLG
✗ borderRadius: 6               → 用 token.borderRadius
✗ fontWeight: 600               → 只允许 400 / 500
✗ margin: 10px                  → 不在尺寸档位内，禁止使用
✗ theme.useToken()              → 统一用 useModel('theme')
```

---

## 九、组件样式示例

```tsx
const token = useModel('theme');

// 卡片
<div style={{
  background: token.colorBgContainer,
  border: `0.5px solid ${token.colorBorder}`,
  borderRadius: token.borderRadiusLG,
  padding: `${token.sizeLG}px`,
}}>

// 页面标题
<h1 style={{
  fontSize: token.fontSizeHeading,
  fontWeight: token.fontWeightMedium,
  color: token.colorText,
  marginBottom: token.sizeSM,
}}>

// 次要文字
<span style={{
  fontSize: token.fontSizeCaption,
  color: token.colorTextDescription,
}}>

// 主按钮（用 antd Button，不手写）
<Button type="primary">提交</Button>
```