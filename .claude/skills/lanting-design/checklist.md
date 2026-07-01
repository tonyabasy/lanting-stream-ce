# checklist.md — 提交前验证

> 每次提交代码前，必须完成以下所有步骤。
> 任何一项不通过，不允许提交。
> 风格基准：claude.ai — 米白底、clay橙、Serif标题、极简无装饰。

---

## 第一步：自动化检查（运行命令）

在 `lanting-web` 目录下执行：

```bash
# 检查1：有没有 hardcode 非品牌颜色
# 允许的品牌色：D97757 C86A47 FDEEE8 F5C4A8
# 允许的中性色：FAF9F6 F5F3EE F0EDE6 FDFCF9 E8E5DF D8D4CC
# 允许的文字色：1A1915 5F5E5A 9B9689 C4C0B8
# 允许的语义色：EAF3DE 27500A 639922 FCEBEB 791F1F E24B4A FAEEDA 633806 BA7517 F1EFE8 888780 B4B2A9
grep -rn --include="*.tsx" --include="*.ts" --include="*.css" --include="*.less" \
  -E "(#[0-9a-fA-F]{3,6})" \
  src/ \
  | grep -v "node_modules" \
  | grep -vE "(D97757|C86A47|FDEEE8|F5C4A8|FAF9F6|F5F3EE|F0EDE6|FDFCF9|E8E5DF|D8D4CC|EEEBE4|1A1915|5F5E5A|9B9689|C4C0B8|EAF3DE|27500A|639922|FCEBEB|791F1F|E24B4A|FAEEDA|633806|BA7517|F1EFE8|888780|B4B2A9|ffeef0|e6ffed|86181d|176f2c|ffffff|000000)"
```

```bash
# 检查2：有没有禁止的 border-radius 值
grep -rn --include="*.tsx" --include="*.ts" --include="*.css" \
  -E "border-?[Rr]adius:\s*(3|5|7|8|9|16|18|20|24)px" \
  src/

# 允许值：4px 6px 10px 12px 50% 999px
```

```bash
# 检查3：有没有禁止的 font-weight
grep -rn --include="*.tsx" --include="*.ts" --include="*.css" \
  -E "font-?[Ww]eight:\s*(600|700|800|bold)" \
  src/
```

```bash
# 检查4：有没有装饰性 box-shadow
grep -rn --include="*.tsx" --include="*.ts" --include="*.css" \
  -E "box-shadow:" \
  src/
# 逐一检查：只允许弹窗阴影和 focus ring，不允许卡片装饰阴影
```

```bash
# 检查5：有没有使用 Ant Design 默认蓝色
grep -rn --include="*.tsx" --include="*.ts" \
  -E "(1890ff|096dd9|type=\"primary\")" \
  src/
# primary type 的 Button 必须手动覆盖颜色为 clay 橙
```

```bash
# 检查6：顶栏有没有错误使用品牌色背景
grep -rn --include="*.tsx" --include="*.ts" \
  -E "topbar|TopBar|top-bar" \
  src/ | xargs grep -l "background" | xargs grep -E "(D97757|1B4B5A|007AFF)"
# 顶栏背景必须是米白 FAF9F6，不能是品牌色
```

```bash
# 一键运行所有检查
echo "=== 检查1：非品牌 hardcode 颜色 ===" && \
grep -rn --include="*.tsx" --include="*.css" \
  -E "(#[0-9a-fA-F]{6})" src/ \
  | grep -v node_modules \
  | grep -vE "(D97757|C86A47|FDEEE8|FAF9F6|F5F3EE|F0EDE6|FDFCF9|E8E5DF|D8D4CC|EEEBE4|1A1915|5F5E5A|9B9689|C4C0B8|EAF3DE|27500A|639922|FCEBEB|791F1F|E24B4A|FAEEDA|633806|BA7517|F1EFE8|888780|B4B2A9|ffeef0|e6ffed|ffffff|000000)" \
&& echo "" && echo "=== 检查2：非规定圆角 ===" && \
grep -rn --include="*.tsx" --include="*.css" \
  -E "border-?[Rr]adius:\s*(3|5|7|8|9|16|18|20|24)px" src/ \
&& echo "" && echo "=== 检查3：禁止字重 ===" && \
grep -rn --include="*.tsx" --include="*.css" \
  -E "font-?[Ww]eight:\s*(600|700|800|bold)" src/ \
&& echo "" && echo "=== 检查4：装饰阴影 ===" && \
grep -rn --include="*.tsx" --include="*.css" \
  -E "box-shadow:" src/ \
&& echo "" && echo "=== 检查5：Ant Design 蓝 ===" && \
grep -rn --include="*.tsx" \
  -E "1890ff|type=\"primary\"" src/ \
; echo "" && echo "=== 全部检查完成 ==="
```

---

## 第二步：自查 Checklist

对照以下清单逐项检查，每项回答"是"才能继续。

### 颜色

- [ ] 品牌色只用 clay 橙 `#D97757`，没有使用墨青、苹果蓝或其他颜色
- [ ] 背景色使用米白体系（`#FDFCF9` / `#FAF9F6` / `#F5F3EE`），不是纯白 `#ffffff`
- [ ] 文字颜色使用暖黑 `#1A1915`，不是纯黑 `#000000` 或 `#333333`
- [ ] 状态颜色使用规定的语义色（绿/红/黄/灰），没有用 Ant Design 默认蓝
- [ ] 顶部导航背景是米白 `#FAF9F6`，不是品牌色

### 圆角

- [ ] 所有圆角只用 4px / 6px / 10px / 12px / 50% / 999px
- [ ] 按钮圆角是 6px
- [ ] 卡片圆角是 10px（不是 8px）
- [ ] 状态标签圆角是 4px，不是胶囊形
- [ ] Logo 容器圆角是 6px

### 字体

- [ ] 页面标题使用 Serif（Georgia）
- [ ] 卡片标题使用 Serif（Georgia）
- [ ] AI 洞察内容使用 Serif（Georgia）
- [ ] 正文、数字、标签使用 Sans（system-ui）
- [ ] 代码、SQL 使用 Mono
- [ ] 字重只用 400 或 500，没有 600 / 700 / bold

### 间距

- [ ] 所有间距是 4 的倍数
- [ ] 卡片内边距是 `13px 15px` 或 `16px`
- [ ] 页面内容区内边距是 `20px 22px`

### 边框

- [ ] 默认边框是 `0.5px solid #E8E5DF`
- [ ] 没有使用 `1px solid` 做普通边框
- [ ] 统计卡片无边框（用背景色差异区分）

### 阴影

- [ ] 卡片没有 box-shadow
- [ ] 只有弹窗、下拉菜单有阴影
- [ ] focus ring 用 `box-shadow: 0 0 0 2px #FDEEE8`

### 布局

- [ ] 顶部导航高度是 48px，背景是 `#FAF9F6`
- [ ] 侧边栏宽度是 48px，背景是 `#F5F3EE`，只有图标
- [ ] 页面内容区背景是 `#FDFCF9`

### 组件行为

- [ ] 主要按钮背景是 clay 橙 `#D97757`，不是蓝色
- [ ] 高危操作有二次确认弹窗，不用 `window.confirm()`
- [ ] AI 通知不打断用户（用 notification，不用 Modal.confirm）
- [ ] 空状态有 Serif 标题 + 引导文字 + 操作按钮

### AI 交互

- [ ] AI 相关标识使用 clay 橙小圆点，不是绿色
- [ ] AI 洞察文字使用 Serif 字体
- [ ] diff 展示使用绿色新增 / 红色删除
- [ ] 审核确认弹窗展示了 AI Review 结论
- [ ] 高危操作需要输入名称确认，按钮在输入前禁用

---

## 第三步：视觉验收

在浏览器里打开页面，对照以下问题：

### 整体感觉

```
□ 整体偏米白暖调，不是冷白或灰白
□ 品牌橙色只出现在少数关键位置（按钮、logo、AI点、激活态）
□ 没有蓝色出现（Ant Design 默认蓝已被覆盖）
□ 没有装饰性阴影和渐变
□ 标题有书写感（Serif），正文干净（Sans）
```

### 具体检查

```
□ 顶部导航是米白底 + 细边框分割，不是品牌色背景
□ Logo 容器是 clay 橙方块，不是墨青
□ 侧边栏激活项是浅橙背景 + 橙色图标
□ 卡片之间用空间分割，边框极细
□ 统计卡片是米白灰底，无边框无阴影
□ 状态标签是方形，不是胶囊
□ AI 洞察区域文字有轻微 Serif 感
```

### 暗色模式（如果支持）

```
□ 米白变成暖深色，不是冷灰
□ clay 橙在暗色下仍然清晰
□ 文字在深色背景下可读
```

---

## 常见错误速查

| 发现的问题 | 正确做法 |
|-----------|---------|
| 背景用了 `#ffffff` | 改为 `#FAF9F6` 或 `#FDFCF9` |
| 文字用了 `#333333` | 改为 `#1A1915` |
| 文字用了 `#999999` | 改为 `#9B9689` |
| 按钮用了 `#1890ff` | 改为 `#D97757` |
| 顶栏用了品牌色背景 | 改为 `#FAF9F6` + 底部细边框 |
| 卡片圆角用了 `8px` | 改为 `10px` |
| 标签用了胶囊形 `999px` | 改为 `4px` |
| 字重用了 `700` | 改为 `500` |
| 标题没有 Serif | 加 `fontFamily: 'Georgia, serif'` |
| AI 内容没有 Serif | 加 `fontFamily: 'Georgia, serif'` |
| 卡片有 `box-shadow` | 删除，改用 `border: 0.5px solid #E8E5DF` |
| 空状态只有"暂无数据" | Serif 标题 + 说明文字 + 操作按钮 |
| 高危操作直接执行 | 加审核确认弹窗 + 输入名称确认 |

---

## 不通过怎么办

自动化检查有输出 → 按文件路径逐一修改，重新运行检查。

Checklist 有未勾选项 → 找到对应代码，对照 `components.md` 修改。

视觉验收有问题 → 对照 `tokens.md` 找规范值，修改后重新验收。

全部通过后才提交。
