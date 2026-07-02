# Lanting Design — 主题定义 Skill

## 触发时机

当用户提到以下任意一种需求时，启用此 Skill：

- "帮我定义主题 / 颜色 / 字体"
- "我想调整设计风格"
- "生成 antd theme"
- "更新 `src/themes/`"

---

## 核心原则（Apple 语义优先）

> 先定角色，再填色值。颜色服务于功能，不是装饰。

1. **角色先于色值** — 每个颜色必须有明确用途名称，不允许在代码里写裸 hex
2. **一个主色** — 只有 `colorPrimary` 随品牌变化，其余中性色固定
3. **中性色决定质感** — 背景、文字、边框占界面 95% 面积，比主色更重要
4. **字体不混用** — 同一字体族，靠字号和字重制造层级，Serif 限用两处

---

## 执行路径（严格按顺序，不可跳步）

### Step 1 — 收集意图

向用户提问，收集以下信息后再继续：

```
Q1: 产品气质关键词？（如：专业严肃 / 清爽轻快 / 温暖亲切）
Q2: 参考产品？（如：Linear、Notion、GitHub）
Q3: 主色偏好？（冷色系 / 暖色系 / 中性 / 已有具体颜色）
Q4: 背景偏好？（纯白 / 米白 / 浅灰）
Q5: Serif 字体用途？（仅 Logo / Logo + 标题 / Logo + AI 内容）
```

不要跳过这步。如果用户已经给了部分答案，只问未回答的问题。

---

### Step 2 — 确定中性色套系

根据用户的背景偏好，从以下三套选一个，**不要混用**：

| 套系 | colorBgBase | secondaryBg | tertiaryBg | 适合气质 |
|------|-------------|-------------|------------|----------|
| 纯白 | `#FFFFFF` | `#F5F5F5` | `#EBEBEB` | 专业、工程感 |
| 米白 | `#FDFCF9` | `#FAF9F6` | `#F5F3EE` | 温暖、人文感 |
| 冷灰 | `#FAFAFA` | `#F4F4F5` | `#E4E4E7` | 现代、克制感 |

文字色（固定，不随套系变化）：

```
colorText（主文字）:          #111111
colorTextSecondary（次要）:   #555555
colorTextTertiary（提示）:    #999999
colorTextDisabled（禁用）:    #CCCCCC
```

边框色（固定）：

```
colorBorder（分隔线）:        #E5E5E5
colorBorderSecondary（强线）: #D4D4D4
```

向用户展示这套中性色并确认，确认后进入 Step 3。

---

### Step 3 — 选定并验证主色

#### 3.1 选色

从用户意图推导候选色，或使用用户提供的颜色。
若用户想从中国传统色里选，使用以下预设：

| 名称 | hex | HSL | 气质 |
|------|-----|-----|------|
| 景泰蓝 | `#2A6A8A` | H204 S53% L36% | 沉稳·技术感 |
| 朱砂 | `#C23B22` | H10 S69% L44% | 热情·生命力 |
| 竹青 | `#527A5F` | H138 S20% L40% | 清雅·自然感 |
| 青莲 | `#8A5A8A` | H300 S21% L45% | 典雅·诗意感 |
| 靛蓝 | `#2A4A7A` | H220 S49% L32% | 深邃·专业感 |

#### 3.2 验证三个条件（必须全部通过）

```
✓ 条件 1：主色放白字对比度 ≥ 4.5:1（WCAG AA）
  告知用户到 whocanuse.com 输入主色验证，或自行计算。
  如果不过，将 Lightness 降低 5% 后重试。

✓ 条件 2：饱和度 S < 80%（过高会刺眼）

✓ 条件 3：主色 + 透明度 8% 的背景在 secondaryBg 上可见
  计算：primaryBg = 主色 hex + "14"（如 #2A6A8A14）
```

#### 3.3 推导品牌配套色（保持 Hue 和 Saturation 不变，只改 Lightness）

```
colorPrimaryHover  = Lightness - 8%   按钮 hover
colorPrimaryActive = Lightness - 16%  按钮 pressed
colorPrimaryBg     = 主色 + 8% 透明度  选中行、激活导航底色
colorPrimaryBorder = Lightness + 20%  输入框 focus 边框
```

#### 3.4 推导语义色（固定色相 + 跟随主色调性）

语义色不写死，从主色的 S（饱和度）和 L（明度）推导，保证整套颜色调性一致。

**推导公式：**

```
colorSuccess = hsl(130, S × 0.85, L)
  绿，H 固定 130，饱和度略降避免太艳

colorWarning = hsl(38,  S × 1.3,  L + 6%)
  琥珀黄，H 固定 38，饱和度加强 + 亮度 +6%
  （黄色视觉权重低，不加强显不出警告感）

colorError   = hsl(5,   S × 1.1,  L)
  红，H 固定 5，饱和度略加强

colorInfo    = hsl(210, S,         L)
  蓝，H 固定 210，完全复用主色 S 和 L
```

**以景泰蓝 `#2A6A8A`（H=204, S=53%, L=36%）为例：**

| 语义 | 计算 | hex |
|------|------|-----|
| success | hsl(130, 45%, 36%) | `#2D7A3A` |
| warning | hsl(38,  69%, 42%) | `#A06820` |
| error   | hsl(5,   58%, 38%) | `#8F2A1E` |
| info    | hsl(210, 53%, 36%) | `#1E5C8A` |

**执行步骤：**
1. 将主色 hex 转为 HSL（工具：hsl.to 或浏览器 DevTools color picker）
2. 代入公式，计算四个值
3. 转回 hex
4. 展示给用户确认

向用户展示主色 + 品牌配套色 + 四个语义色，全部确认后进入 Step 4。

---

### Step 4 — 定义字体规则

#### 字体栈

```
sans（所有 UI 文字）:
  'system-ui, -apple-system, sans-serif'

mono（代码、数值）:
  'ui-monospace, "SF Mono", Consolas, monospace'

serif（限用两处）:
  '"Playfair Display", "Noto Serif SC", Georgia, serif'
```

#### Serif 使用限制（与用户确认后写入注释）

根据 Step 1 Q5 的答案，明确 Serif 的两处使用场景，写进 tokens.ts 注释里。
常见组合：
- Logo 品牌名 + AI 洞察正文
- Logo 品牌名 + 页面一级标题

**第三处绝对不用 Serif。**

#### 字号层级（固定）

```
fontSize:   14px  antd 基础字号，组件从此推导
fontSizeSM: 12px  badge、行号、caption
fontSizeLG: 16px  卡片标题
fontSizeXL: 17px  页面标题（用 Serif）
lineHeight: 1.6
```

#### 字重只用两个值

```
400  regular — 正文、次要信息
500  medium  — 标题、强调、按钮文字
```

**禁止使用 600 / 700 / bold。**

---

### Step 5 — 生成最终产物

以上步骤全部确认后，生成以下两个文件并写入项目。

---

#### 文件 1：`src/themes/tokens.ts`

所有设计决策的唯一来源文件。注释要完整，说明每个值的用途和推导依据。

```ts
/**
 * Lanting Design Tokens
 *
 * 修改主题只需改这个文件。
 * 不要在组件里写裸 hex — 引用此处的变量或对应的 CSS 变量。
 *
 * 主色：（填入颜色名和 hex）
 * 主色 HSL：H=xxx S=xx% L=xx%
 *
 * Serif 字体使用范围（限两处）：
 *   1. 顶栏 Logo「Lanting Stream」文字
 *   2. （根据 Step 1 Q5 填写）
 */

export const tokens = {
  // ── 品牌色 ─────────────────────────────────────────
  colorPrimary:        '主色 hex',
  colorPrimaryHover:   'L-8% hex',
  colorPrimaryActive:  'L-16% hex',
  colorPrimaryBg:      '主色 + 透明度 8% hex（如 #2A6A8A14）',
  colorPrimaryBorder:  'L+20% hex',

  // ── 语义色（从主色 S/L 推导，见 SKILL.md Step 3.4）──
  // 主色 S=xx%, L=xx%
  colorSuccess: 'hsl(130, S×0.85, L) → hex',   // 绿
  colorWarning: 'hsl(38,  S×1.3,  L+6%) → hex', // 琥珀黄
  colorError:   'hsl(5,   S×1.1,  L) → hex',    // 红
  colorInfo:    'hsl(210, S,       L) → hex',    // 蓝

  // ── 背景（根据 Step 2 选定的套系填入）────────────
  colorBgBase:         '套系 background hex',
  colorBgContainer:    '套系 secondaryBg hex',
  colorBgLayout:       '套系 background hex',
  colorBgElevated:     '套系 secondaryBg hex',
  colorFillQuaternary: '套系 tertiaryBg hex',

  // ── 文字（固定）────────────────────────────────────
  colorTextBase:       '#111111',
  colorTextSecondary:  '#555555',
  colorTextTertiary:   '#999999',
  colorTextDisabled:   '#CCCCCC',

  // ── 边框（固定）────────────────────────────────────
  colorBorder:          '#E5E5E5',
  colorBorderSecondary: '#D4D4D4',
  colorSplit:           '#E5E5E5',

  // ── 字体 ───────────────────────────────────────────
  fontFamily:     'system-ui, -apple-system, sans-serif',
  fontFamilyCode: 'ui-monospace, "SF Mono", Consolas, monospace',
  // Serif 通过 CSS 变量 --font-serif 引用，限两处，见顶部注释

  // ── 字号 ───────────────────────────────────────────
  fontSize:   14,   // antd 基础字号
  fontSizeSM: 12,   // badge、行号、caption
  fontSizeLG: 16,   // 卡片标题
  fontSizeXL: 17,   // 页面标题（Serif）
  lineHeight: 1.6,

  // ── 形状 ───────────────────────────────────────────
  borderRadius:   6,
  borderRadiusSM: 4,
  borderRadiusLG: 10,
  borderRadiusXL: 12,

  // ── 控件高度 ───────────────────────────────────────
  controlHeight:   36,
  controlHeightSM: 28,
  controlHeightLG: 44,

  // ── 动效 ───────────────────────────────────────────
  motionDurationMid:  '0.15s',
  motionDurationSlow: '0.25s',
} as const;

export type DesignTokens = typeof tokens;
```

---

#### 文件 2：`src/themes/index.ts`

antd ThemeConfig 导出，从 tokens.ts 读取，不写任何裸 hex。

```ts
import type { ThemeConfig } from 'antd';
import { tokens as t } from './tokens';

const theme: ThemeConfig = {
  token: {
    // 品牌色
    colorPrimary: t.colorPrimary,
    colorSuccess:  t.colorSuccess,
    colorWarning:  t.colorWarning,
    colorError:    t.colorError,
    colorInfo:     t.colorInfo,

    // 背景
    colorBgBase:         t.colorBgBase,
    colorBgContainer:    t.colorBgContainer,
    colorBgLayout:       t.colorBgLayout,
    colorBgElevated:     t.colorBgElevated,
    colorFillQuaternary: t.colorFillQuaternary,

    // 文字
    colorTextBase:       t.colorTextBase,
    colorTextSecondary:  t.colorTextSecondary,
    colorTextTertiary:   t.colorTextTertiary,
    colorTextDisabled:   t.colorTextDisabled,

    // 边框
    colorBorder:          t.colorBorder,
    colorBorderSecondary: t.colorBorderSecondary,
    colorSplit:           t.colorSplit,

    // 字体
    fontFamily:     t.fontFamily,
    fontFamilyCode: t.fontFamilyCode,
    fontSize:       t.fontSize,
    lineHeight:     t.lineHeight,

    // 形状
    borderRadius:   t.borderRadius,
    borderRadiusSM: t.borderRadiusSM,
    borderRadiusLG: t.borderRadiusLG,
    borderRadiusXL: t.borderRadiusXL,

    // 控件
    controlHeight:   t.controlHeight,
    controlHeightSM: t.controlHeightSM,
    controlHeightLG: t.controlHeightLG,

    // 动效
    motionDurationMid:  t.motionDurationMid,
    motionDurationSlow: t.motionDurationSlow,
  },

  components: {
    Button: {
      borderRadius:  t.borderRadius,
      primaryShadow: 'none',
      defaultShadow: 'none',
      dangerShadow:  'none',
    },
    Input: {
      borderRadius:      t.borderRadius,
      activeBorderColor: t.colorPrimary,
      hoverBorderColor:  t.colorBorderSecondary,
    },
    Select: {
      borderRadius: t.borderRadius,
    },
    Table: {
      headerBg:     t.colorFillQuaternary,
      rowHoverBg:   t.colorPrimaryBg,
      borderColor:  t.colorBorder,
      borderRadius: t.borderRadiusLG,
    },
    Menu: {
      itemBorderRadius:  t.borderRadius,
      itemSelectedBg:    t.colorPrimaryBg,
      itemSelectedColor: t.colorPrimary,
      itemHoverBg:       t.colorFillQuaternary,
    },
    Modal: {
      borderRadiusLG: t.borderRadiusXL,
    },
    Card: {
      borderRadius: t.borderRadiusLG,
    },
    Tag: {
      borderRadiusSM: t.borderRadiusSM,
    },
    Tabs: {
      inkBarColor:       t.colorPrimary,
      itemSelectedColor: t.colorPrimary,
      itemHoverColor:    t.colorPrimary,
    },
  },
};

export default theme;
```

---

### Step 6 — 验收检查

生成并写入文件后，逐条确认：

```
□ 1. tokens.ts 顶部注释写明了主色名称、hex、HSL 值
□ 2. tokens.ts 语义色注释写明了推导公式和计算过程
□ 3. tokens.ts 里没有写死的裸 hex（所有值有注释说明）
□ 4. index.ts 里没有任何 hex，全部引用 t.xxx
□ 5. app.tsx 的 ConfigProvider 使用了新的 theme import
□ 6. Serif 字体使用场景已写入 tokens.ts 顶部注释，不超过两处
□ 7. 浏览器确认：主色按钮放白字可读
```

全部通过，完成。

---

## 禁止事项

- 组件文件里不允许出现裸 hex，必须用 CSS 变量或 token 引用
- 不允许同时维护多套主题文件，只有一个 `tokens.ts`
- 不允许使用 font-weight 600 或 700
- Serif 字体不允许出现在第三处以上
- Map Token 和 Alias Token 不要手动设置（如 `colorPrimaryBg`、`colorLink`），让 antd 算法自动推导
- 语义色必须从主色推导，不允许直接写固定 hex（如 `#34A853`）

---

## 附：antd Token 层级说明

```
你设置的（Seed Token）
  colorPrimary, colorBgBase, colorTextBase, fontSize, borderRadius ...
        ↓  antd algorithm 自动推导
中间层（Map Token）
  colorPrimaryBg, colorPrimaryHover, colorPrimaryBorder ...
        ↓
语义层（Alias Token）
  colorLink = colorPrimary, colorBgSpotlight ...
        ↓
组件覆盖（Component Token）
  components: { Button: {}, Input: {} ... }
```

**只设置 Seed Token + 必要 Component Token。中间层让算法处理。**
