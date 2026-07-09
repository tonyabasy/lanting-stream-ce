# 主题配置概要设计

## 概述

主题系统分为三层：数据层（JSON）、解析层（`themes/index.ts`）、应用层（ConfigProvider + useModel + CSS 变量）。后端可下发主题配置覆盖前端默认值，前端始终有兜底。

---

## 目录结构

```
src/
├── themes/
│   ├── theme-default-light.json   ← 唯一数据源，打包进 bundle
│   └── index.ts                   ← 类型定义 + 解析函数 + CSS 变量注入
├── models/
│   └── useTheme.ts                ← 合并远程主题，供 TSX 组件使用
└── layouts/
    ├── index.tsx                  ← 主 Layout，含 ConfigProvider
    └── LoginLayout.tsx            ← 登录页 Layout，静态主题
```

---

## 数据流

```
theme-default-light.json（唯一数据源）
        ↓ flattenTheme()
    DEFAULT_TOKEN
        ├─ injectCSSVars()        同步注入默认 CSS 变量（bundle 加载时）
        └─ getInitialState()      异步 fetch 远程主题
               ↓ merge + injectCSSVars()
           useTheme()             返回合并后的 LantingToken
               ↓ 两条路径并行
    useModel('theme')             CSS / Less 文件
    TSX 组件取 token 值           var(--lt-xxx)
               ↓
         toAntdTheme(token) → ConfigProvider → antd 组件跟着主题变
```

---

## 一、数据层：theme-default-light.json

嵌套 JSON，按语义分组。字段分类：

```
brand / background / border / text / semantic
font / fontSize / fontWeight / borderRadius / size / boxShadow
```

**关于 `size` 命名：**
原 `spacing` 改为 `size`。这套数值是整个产品的尺寸基准网格，padding / margin / gap / height 都可以取用，`size` 比 `spacing` 语义更准确。

---

## 二、解析层：themes/index.ts

三个核心函数 + 模块入口：

| 函数 | 作用 |
|------|------|
| `flattenTheme(raw)` | 嵌套 JSON → 扁平 LantingToken |
| `injectCSSVars(token)` | LantingToken → CSS 变量注入 `:root` |
| `toAntdTheme(token)` | LantingToken → antd ThemeConfig |
| 模块顶层 | `injectCSSVars(DEFAULT_TOKEN)`，bundle 加载时同步执行 |

**CSS 变量命名规范：**
统一加 `--lt-` 前缀（Lanting 缩写），避免与 antd、第三方库冲突。

```
--lt-color-primary     颜色类
--lt-font-size-body    字号类
--lt-radius-md         圆角类
--lt-size-md           尺寸类
--lt-shadow-card       阴影类
```

---

## 三、应用层

**CSS 变量注入时机（两阶段）：**

```
阶段一：bundle 加载时同步注入默认值，React 渲染前完成，不闪烁
阶段二：远程主题 fetch 成功后覆盖注入，失败则保持默认值
未来：切换主题时调 injectCSSVars(newToken)，全局一次性更新
```

**各层职责：**

| 文件 | 职责 |
|------|------|
| `models/useTheme.ts` | 合并远程主题，重新注入 CSS 变量，返回 LantingToken |
| `app.tsx` | fetch 远程主题，存入 initialState，失败不阻塞登录 |
| `layouts/index.tsx` | useModel 读 token，传给 ConfigProvider |
| `layouts/LoginLayout.tsx` | 用静态默认主题（此时 Model Provider 未就绪） |

---

## 四、组件使用规范

| 场景 | 方式 |
|------|------|
| TSX 动态样式 | `useModel('theme')` → `token.colorPrimary` |
| CSS / Less 文件 | `var(--lt-color-primary)` |
| 两者数据来源相同 | 不需要手动同步 |

---

## 五、约束与禁止事项

```
✗ CSS 文件里 hardcode：color: #4A8A6A / padding: 12px
✗ TSX 里 hardcode：color: '#4A8A6A' / fontSize: 14
✗ TSX 里用 var()：style={{ color: 'var(--lt-color-primary)' }}（失去类型安全）
✗ 使用 theme.useToken()，统一用 useModel('theme')
✗ rootContainer 里用 useModel（Model Provider 未就绪）
✗ 主题文件放 public/ 目录（会被直接暴露）
✗ 自定义非规范档位：--lt-size-10（不在八档之内）
```

---

## 六、扩展性

- **新增 token**：JSON 加字段 → 接口加类型 → injectCSSVars 加注入，三处同步
- **新增主题**：新建 `theme-xxx.json`，结构相同，解析函数无需修改
- **Dark Mode**：新建 `theme-default-dark.json`，切换时调 `injectCSSVars(darkToken)`，所有组件自动响应

---

## 七、待 Review 项

### CSS 变量注入时机优化（低优先级）

**现状**

CSS 变量在 `themes/index.ts` 模块加载时通过 `injectCSSVars(DEFAULT_TOKEN)` 同步注入，依赖 `document` 对象存在。当前是 CSR 场景，没有问题。

**潜在问题**

若未来引入 SSR，服务端没有 `document`，模块加载时调用 `injectCSSVars` 会直接报错。

**优化方向：构建时注入（方案 A）**

写一个 Umi 插件，在构建时读取 `theme-default-light.json`，转成 CSS 变量字符串，通过 `api.addHTMLStyles` 注入到 HTML `<head>` 的 `<style>` 标签里：

```html
<head>
  <style>
    :root {
      --lt-color-primary: #4A8A6A;
      --lt-size-md: 12px;
      ...
    }
  </style>
</head>
```

`themes/index.ts` 里的 `injectCSSVars(DEFAULT_TOKEN)` 调用可以删掉，CSS 变量在 HTML 解析时就已经存在，零延迟，无 JS 依赖。

**触发条件**

满足以下任一条件时启动改造：
- 引入 SSR
- 对首屏性能有严格要求
- 支持服务端按用户偏好输出不同主题

**当前结论**

CSR 场景下现有方案足够，暂不改造。