# 文件级发布设计

> 按场景驱动逐步推导模型。本文档描述**设计意图与关键决策**，具体实现以代码为准。

---

## 用户场景

### 场景 1：最简发布

#### 叙述

1. user1 在 file1 点「提交」按钮（git commit + 加入待发布池），file2 同样操作。
2. 此时「待发布列表」中包含 file1、file2（user1 提交的）和 file3、file4（user2 提交的）。
3. user2 对 user1 说：「帮我把 file3 一起发了吧」。
4. user1 选中 file1、file2、file3，执行「发布」。
5. 「已发布列表」中出现一条 Pub1，包含 file1、file2、file3。file4 未被选中，仍留待发布列表。

#### 关键点

1. **「提交」** = git commit + 加入待发布池，一个原子动作。
2. **待发布列表是「文件级」的候选集合**，不是批次级。条目是「文件（及其最近 commit 版本）」，来自不同用户、不同时间混在一起。
3. **发布是跨用户的**：user1 可以发布 user2 提交的文件。
4. **发布是「选择性打包」**：只含选中文件，未选中的继续留池。
5. **发布批次是快照**：发布那一刻定格各自 commit_hash，之后继续修改提交不影响 Pub1。
6. **发布即上线**：已发布列表 = 线上部署回滚版本列表。

---

### 场景 2：代码审核（CR 概念）

#### 叙述

1. user1 提交 file1、file2，口头邀请 user2 做 code review（邀请是系统外的）。
2. user2 写 CR 评价，file1 的 CR 未通过。
3. user1 **也可以直接发布** file1、file2 —— CR 不阻断发布。
4. user1 重新修改 file1 并再次提交（新 commit）。
5. user2 对新版本重审，审核通过。
6. user1 发布 file1、file2。

#### 关键点

1. **CR 不是系统强制流程**，只是辅助监督。发布动作不检查 CR 状态。
2. **CR 挂在单个文件（及其当前版本）上**，不挂在发布批次上。
3. **CR 随版本演进**：文件新 commit 后旧 review 因 hash 不匹配而"过时"。当前 CR 状态 = 与 `latest_commit_hash` 匹配的最新 review。
4. **CR 是建议性的**：通过/不通过不阻止任何后续动作。

---

### 场景 3：撤销提交

#### 叙述

1. user1 提交了 file1、file2、file3（各自创建 commit 并进入待发布池）。
2. user1 发现 file3 提交错了，对 file3 执行「撤销」。
3. user1 发布 file1、file2（file3 不在发布批次中）。

#### 关键点

1. **「撤销」作用于上线前的待发布候选**，不撤销已发布批次。
2. **撤销 = 把文件从待发布池移除（取消暂存）**，与发布批次无关。
3. **撤销是文件级、可逆的**：file3 之后仍可重新提交、重新圈定、再发布。
4. **不回退 Git commit**：撤销仅将文件移出待发布池。

---

### 场景 4：同一文件多次提交、跨用户

#### 叙述

1. user1 修改 file1，点击「提交」→ git commit（有变更）+ 加入待发布池。
2. user1 忘记已提交，又点一次「提交」（内容无变更）。
3. 系统检测无变更，提示"无需重复提交"。
4. user2 接手 file1，修改内容后点击「提交」→ git commit（有变更）；file1 已在池中 → 幂等跳过入池。
5. 待发布列表动态从 `FileIndex.latest_commit_hash` 读取，反映 user2 的最新 commit。

#### 关键点

1. **「提交」= git commit + 入池，是一个原子动作**。无变更 → git 层中断，不进池。
2. **无变更在 git 层即被阻断**，不走入池判断。
3. **池中重复提交幂等**：已池中不新建行，不改变状态。新 commit 通过 `FileIndex` 实时反映到列表。
4. **跨用户接手不改变池记录归属**：`created_by` 保持为最初加入者。

---

### 场景 5：Code Review（CR 评审流程）

#### 叙述

1. user1 提交了 file1、file2、file3，邀请 user2 做 code review。
2. user2 打开「待发布列表」页，按修改人找到文件。
3. user2 点开某个文件的 Code Diff 视图。
4. 系统将文件**当前提交内容**与**上次发布版本的快照**做 git diff 呈现。
5. user2 看完给出评审结论（通过/不通过）。
6. 评审结果反映到待发布列表中该文件的状态标签上。

#### 关键点

1. **CR 依赖 diff 能力，diff 基准是上次发布版本的快照**。从未发布过 → diff 基准为空树。
2. **CR 入口在待发布列表页面**，不需要独立导航。
3. **评审动作为单文件、单版本**，绑定 `(file_id, commit_hash)`。

---

### 场景 6：反复评审与修改结论

#### 叙述

1. user1 提交 file1，邀请 user2 做 code review。
2. user2 提交评审结论——不通过，附带意见。
3. user2 发现看错了，调整结论为"通过"，更新意见。
   - 系统检测到 user2 对该 `(file_id, commit_hash)` 已有评审 → **覆盖更新**。
4. user1 看到结果为"通过"后发布 file1。

#### 关键点

1. **同一 reviewer 对同一 `(file_id, commit_hash)` 做 upsert**（已存在 → 更新，不存在 → 新插）。
   - 不同 reviewer 各自独立记录，互不覆盖。
2. **评审可删除**：reviewer 可删除自己的记录，非本人拒绝。
3. **评审不阻断发布**。

---

## 提炼的模型

### 待发布集合

用户主动圈定的文件候选集。进入该集合之前文件必经过一次 commit。

关系：`有 commit ⊋ 在待发布集合`（有 commit 不自动进入集合；提交动作显式写入）。

特点：**必有 commit 且是最新一次 commit**；**在集合里仍可继续提交（PR 语义）**。

### 代码审核（CR）

CR 是对待发布池中单个文件候选的评审注释，非系统内强制审批流：

- 不提供"请求 review"动作，reviewer 直接对池中文件写评价
- review 记录绑定 `(file_id, commit_hash, reviewer, comment, result, create_time)`，`result ∈ {APPROVED, REJECTED}`
- 某文件的"当前 CR 状态" = 与该文件当前 `latest_commit_hash` 匹配的最新 review
- 同一 reviewer 对同一版本 upsert，不同 reviewer 各自保留独立记录
- CR 不阻断发布：发布动作不检查 CR 状态

### 状态流

```
文件 ──提交(加入候选集)──► [COMMITTED] ──发布──► [PUBLISHED, publish_id=批次id]
                              │
                              └──取消(撤销候选)──► [CANCELED]
```

- review 链路可选，不阻断发布
- 已发布不可撤销
- 同一 `file_id` 同一时刻仅一条 `COMMITTED` 行；提交幂等；CANCELED 后重新提交追加新行

### 模型草图

```
待发布集合（用户圈定的暂存池，文件级，共享）
┌──────────────────────────────────────────┐
│ file1  created_by=user1  ← 实时最新 commitX │
│ file2  created_by=user1  ← 实时最新 commitY │
│ file3  created_by=user2  ← 实时最新 commitZ │
│ file4  created_by=user2  ← 实时最新 commitW │
└──────────────────────────────────────────┘
        │ user1 选中 file1,2,3 发布
        ▼
发布批次 Pub1（快照发布那一刻各自的最新 commit）
┌──────────────────────────┐
│ Pub1  published_by=user1 │
│  ├─ file1 @ commitX'     │  ← 发布时重新读取各自最新 commit
│  ├─ file2 @ commitY'     │
│  └─ file3 @ commitZ'     │
└──────────────────────────┘
   file4 未选中，留池中
```

---

## 关键决策

| 决策 | 说明 |
|---|---|
| 提交 = git commit + 入池，原子 | 一个接口调用完成两步，无变更时 git 层中断 |
| 发布只定格 commitHash | 读 `FileIndex.latest_commit_hash` 写入快照，不搞 git 操作 |
| 发布与 Git 耦合在 PublishService | PublishService 注入 GitFileService 做 commit 和 diff |
| diff 基准为上次发布版本 | 从未发布 → 空树；已发布 → 上次批次定格的 hash |
| CR upsert 同一 reviewer 同版本 | 不同 reviewer 各自独立、不互盖 |
| 评审可删除、发布不可删除 | 自己的评审可删；发布批次不可删 |
| 主键 UUID | 发布批次 id 为 Varchar(64) UUID，未来可切雪花 |
