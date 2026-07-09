# tokens.md — 设计令牌

> 所有颜色、尺寸、圆角、字体的唯一来源。
> 写代码时遇到任何视觉值，必须从这里取，不允许自己发明。
> 风格基准：claude.ai — 米白底、clay 橙、Serif 标题、极简无装饰。

---

## 一、颜色

### 品牌色（Clay 橙，来自 claude.ai）

```css
--ls-brand:        #D97757;  /* 主品牌色，clay橙，用于主按钮、logo、激活态 */
--ls-brand-hover:  #C86A47;  /* hover 加深 */
--ls-brand-tint:   #FDEEE8;  /* 极浅橙，用于选中行背景、激活态填充 */
--ls-brand-border: #F5C4A8;  /* 品牌色边框 */
```

### 使用规则

| 场景 | 使用的颜色 |
|------|-----------|
| 主要按钮背景 | `--ls-brand` |
| 主要按钮 hover | `--ls-brand-hover` |
| Logo 背景 | `--ls-brand` |
| 链接文字、图标高亮 | `--ls-brand` |
| 导航选中项背景 | `--ls-brand-tint` |
| 导航选中项图标 | `--ls-brand` |
| 选中行背景 | `--ls-brand-tint` |
| AI 相关点缀 | `--ls-brand` |

### 中性色（米白体系，来自 claude.ai）

```css
/* 背景层次：米白不是纯白，有轻微暖调 */
--ls-bg-page:      #FDFCF9;  /* 页面底层背景 */
--ls-bg-container: #FAF9F6;  /* 卡片、面板背景 */
--ls-bg-subtle:    #F5F3EE;  /* 侧边栏、统计卡片背景 */
--ls-bg-muted:     #F0EDE6;  /* 标签、chip 背景 */

/* 边框 */
--ls-border:       #E8E5DF;  /* 默认边框 */
--ls-border-strong:#D8D4CC;  /* 强调边框，hover 态 */

/* 文字层次 */
--ls-text-primary:   #1A1915;  /* 主文字，近黑带暖调 */
--ls-text-secondary: #5F5E5A;  /* 次级文字 */
--ls-text-muted:     #9B9689;  /* 说明、占位文字 */
--ls-text-disabled:  #C4C0B8;  /* 禁用状态 */
```

### 语义色（状态）

直接使用以下固定值，不使用 Ant Design 默认蓝色语义色：

```css
/* 运行中 — 绿色 */
--ls-success-bg:   #EAF3DE;
--ls-success-text: #27500A;
--ls-success-dot:  #639922;

/* 异常 — 红色 */
--ls-danger-bg:    #FCEBEB;
--ls-danger-text:  #791F1F;
--ls-danger-dot:   #E24B4A;

/* 警告 — 黄色 */
--ls-warning-bg:   #FAEEDA;
--ls-warning-text: #633806;
--ls-warning-dot:  #BA7517;

/* 停止 — 灰色 */
--ls-stopped-bg:   #F1EFE8;
--ls-stopped-text: #888780;
--ls-stopped-dot:  #B4B2A9;
```

### 禁止事项

```css
/* ❌ 错误：hardcode 非品牌颜色 */
color: #333333;
background: #ffffff;       /* 纯白，不是米白 */
border: 1px solid #e0e0e0;
background: #1890ff;       /* Ant Design 默认蓝，禁止 */
color: #007AFF;            /* 苹果蓝，禁止 */

/* ✅ 正确：使用 Token */
color: var(--ls-text-primary);
background: var(--ls-bg-container);
border: 0.5px solid var(--ls-border);
```

---

## 二、圆角

claude.ai 风格：圆角克制，不夸张，卡片用 10px。

```css
--radius-xs:   4px;    /* 标签、badge、小 chip */
--radius-sm:   6px;    /* 按钮、输入框、导航项 */
--radius-md:   10px;   /* 卡片、面板 */
--radius-lg:   12px;   /* 模态框、大弹窗 */
--radius-full: 999px;  /* 胶囊形（慎用，claude.ai 很少用胶囊） */
```

### 使用规则

| 元素 | 圆角值 |
|------|-------|
| 按钮 | `6px` |
| 输入框 | `6px` |
| 状态标签 / badge | `4px` |
| 导航选中项 | `6px` |
| 卡片 | `10px` |
| 模态框 | `12px` |
| 头像 | `50%`（圆形） |
| Logo 容器 | `6px` |
| 顶部导航栏 | `0`（全宽） |

### 禁止事项

```css
/* ❌ 错误：不在规定值里 */
border-radius: 3px;
border-radius: 8px;
border-radius: 16px;
border-radius: 20px;
border-radius: 24px;

/* ✅ 正确：只用规定值 */
border-radius: 4px;
border-radius: 6px;
border-radius: 10px;
border-radius: 12px;
```

---

## 三、尺寸

基础单位 `4px`，所有尺寸必须是 4 的倍数。claude.ai 风格给足呼吸空间，不要太紧。

```css
--space-1:  4px;
--space-2:  8px;
--space-3:  12px;
--space-4:  16px;
--space-5:  20px;
--space-6:  24px;
--space-8:  32px;
--space-10: 40px;
```

### 常用场景

| 场景 | 尺寸值 |
|------|-------|
| 图标与文字之间 | `8px` |
| 表单项之间 | `16px` |
| 卡片内边距 | `16px` 或 `20px` |
| 卡片之间的尺寸 | `12px` |
| 页面内容区内边距 | `20px 22px` |
| section 之间 | `24px` 或 `32px` |
| 统计卡片内边距 | `12px 14px` |
| 作业列表行尺寸 | `6px 0`（上下） |

### 禁止事项

```css
/* ❌ 错误：非4倍数 */
padding: 5px 10px;
margin: 7px;
gap: 15px;

/* ✅ 正确 */
padding: 4px 8px;
gap: 12px;
```

---

## 四、字体

claude.ai 的核心特征：**标题用 Serif，正文用 Sans，两者切换传达层次。**

```css
/* 字族 */
--font-serif: Georgia, 'Times New Roman', serif;  /* 页面标题、卡片标题 */
--font-sans:  system-ui, -apple-system, sans-serif; /* 正文、数字、标签 */
--font-mono:  'SF Mono', 'Fira Code', monospace;  /* 代码、SQL */

/* 字号 */
--font-xs:   11px;  /* 时间戳、辅助信息（谨慎使用） */
--font-sm:   12px;  /* 表格内容、次级标签、说明文字 */
--font-base: 14px;  /* 默认正文 */
--font-md:   16px;  /* 页面标题（Serif） */
--font-lg:   18px;  /* 大标题（极少用） */
--font-xl:   22px;  /* 仪表盘大数字 */

/* 字重 */
--font-regular: 400;  /* 正文 */
--font-medium:  500;  /* 标题、强调 */
/* 禁止使用 600、700、bold */
```

### 层次规则

```
页面标题    16px / 500 / Serif    var(--ls-text-primary)
卡片标题    12px / 500 / Serif    var(--ls-text-primary)
正文        14px / 400 / Sans     var(--ls-text-primary)
次级说明    12px / 400 / Sans     var(--ls-text-secondary)
辅助信息    11px / 400 / Sans     var(--ls-text-muted)
大数字      22px / 500 / Sans     var(--ls-text-primary)
代码/SQL    13px / 400 / Mono     var(--ls-text-primary)
```

### 禁止事项

```css
/* ❌ 错误 */
font-weight: bold;
font-weight: 700;
font-weight: 600;
font-size: 24px;   /* 太大 */
font-size: 13px;   /* 非规定字号（用12或14） */

/* ✅ 正确 */
font-weight: 500;
font-family: Georgia, serif;  /* 标题 */
font-size: 16px;
```

---

## 五、边框

```css
/* 默认边框：所有卡片、输入框、分割线 */
border: 0.5px solid var(--ls-border);

/* 强调边框：hover 态 */
border: 0.5px solid var(--ls-border-strong);

/* 品牌色边框：选中的卡片、激活的输入框 */
border: 1px solid var(--ls-brand);
```

### 禁止事项

```css
/* ❌ 错误 */
border: 1px solid #e0e0e0;   /* hardcode 且太重 */
border: 2px solid #ccc;       /* 太重 */
border: 1px solid var(--ls-border);  /* 普通边框不用 1px */

/* ✅ 正确 */
border: 0.5px solid var(--ls-border);
```

---

## 六、阴影

claude.ai 几乎不用装饰性阴影，用边框和背景色差异区分层次。

```css
/* 不允许装饰性阴影 */
/* 只在弹窗、下拉菜单使用系统级阴影 */

卡片：         不加阴影，用 border + 背景色区分
下拉菜单：     box-shadow: 0 4px 16px rgba(0,0,0,0.08)
模态框：       box-shadow: 0 8px 32px rgba(0,0,0,0.12)
focus ring：   box-shadow: 0 0 0 2px var(--ls-brand-tint)
```

### 禁止事项

```css
/* ❌ 错误：卡片加装饰阴影 */
.card { box-shadow: 0 4px 12px rgba(0,0,0,0.1); }
.card { box-shadow: 0 2px 8px rgba(0,0,0,0.15); }

/* ✅ 正确：卡片只用边框 */
.card { border: 0.5px solid var(--ls-border); }
```

---

## 七、过渡动画

```css
/* 标准过渡 */
transition: background-color 0.15s ease, color 0.15s ease, border-color 0.15s ease;

/* 按钮 hover */
transition: background-color 0.15s ease;
```

### 禁止事项

```css
/* ❌ 错误：过渡太慢、太花哨 */
transition: all 0.5s cubic-bezier(...);
animation: fadeIn 0.8s ease;
transition: all 0.3s ease;   /* 0.3s 对 UI 来说太慢 */

/* ✅ 正确：0.15s，只过渡需要的属性 */
transition: background-color 0.15s ease;
```

---

## 八、顶部导航配色（特别说明）

claude.ai 顶栏不用品牌色做背景，用米白 + 细边框。

```css
/* ✅ 正确：claude.ai 风格顶栏 */
.topbar {
  background: var(--ls-bg-container);   /* #FAF9F6 米白 */
  border-bottom: 0.5px solid var(--ls-border);
  height: 48px;
}

/* ❌ 错误：品牌色顶栏（苹果/墨青风格，不是 Claude 风格） */
.topbar {
  background: #1B4B5A;
  background: #D97757;
}
```
