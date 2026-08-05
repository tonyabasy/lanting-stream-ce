# Editor Tables 设计

> 从用户场景逐层推导：场景 → 业务流 → 模型 → API → 设计决策。

---

## 用户场景

### 场景1：用户创建 DDL（已确认）

**叙述**

用户 A 需要构建一个 Flink 流计算任务：
- 从 Kafka topic `t_dwd_rw_ord_di` 读取订单数据
- 按 1 分钟自然窗口统计每分钟订单量
- 将结果输出到 Doris primary 表 `dws_rw_ord_cnt_1m`

需要先定义两个表：

**Kafka 源表（Source）**

| 字段 | 类型 | 说明 |
|---|---|---|
| `order_no` | STRING | 订单号 |
| `order_time` | STRING | 下单时间，格式 `yyyy-MM-dd HH:mm:ss` |
| `user_id` | STRING | 用户 ID |
| `city_id` | BIGINT | 城市 ID |
| `pay_amt` | DOUBLE | 订单金额（元） |

连接：Kafka topic `t_dwd_rw_ord_di`

**Doris 结果表（Sink）**

| 字段 | 类型 | 说明 |
|---|---|---|
| `dt` | DATE | 日期（分区字段） |
| `stat_time` | DATETIME | 统计时间（分钟，主键） |
| `pay_ord_cnt` | BIGINT | 订单量 |

连接：Doris primary 表 `dws_rw_ord_cnt_1m`

**操作路径**

1. 用户进入 Tables 视图 → 点击新建 → 选择「DDL 表」
2. 在表单模式中填写表名、连接器类型（Kafka / Doris）、字段列表
3. 点击「确定」保存 → 写盘 `tables/xxx.ddl`，触发 Table Index 更新（代码提示可用）
4. 在 SQL 文件中直接引用表名编写 Flink SQL

---

### 场景2：用户修改数据源（已确认）

**叙述**

1. 用户在 Tables 视图中双击 `dws_rw_ord_cnt_1m.ddl` → 编辑器打开，默认表单模式
2. 在字段表格中新增一行 `pay_ord_amt` / `DOUBLE` / （注释）
3. 点击「确定」保存 → 写盘，触发 DB Table Index 更新
4. 点击「提交」（git commit + 入待发布池）
5. 在待发布列表中选中该文件，点击「发布」→ 执行 File 发布

**关键决策**

- 表单模式**没有自动保存**，用户显式点「确定」
- 文本模式有 1.5s 自动保存
- Table Index 是**磁盘文件索引**，非 Git 索引。保存到磁盘即触发索引更新，与 commit/发布解耦

---

### 场景3：用户从表单切换到文本模式继续编辑（已确认）

**叙述**

1. 用户在表单模式新增了 `pay_ord_amt` 字段（**未点确定，未保存**）
2. 想到还需要加 `pay_user_cnt`、`pay_predict_amt` 等更多字段，表单一行行加效率太低
3. 点击 Segmented 切换到「文本」模式
4. CodeMirror 中**已包含**刚才表单中未保存的 `pay_ord_amt`（序列化器将当前 DdlFormData 实时带入）
5. 在文本模式下一口气新增 10 个字段
6. 文本模式 1.5s 自动保存，无需手动操作

**关键点**

- 表单 → 文本切换时，序列化器将当前表单内存状态（含未保存的修改）实时序列化为 DDL 文本写入 CodeMirror
- 之后文本模式的自动保存将包含所有修改一并写盘

---

### 场景4：从数据库拉取 Table Schema（草稿，待定技术方案）

**叙述**

1. 用户在 Doris 中新建了一张表
2. 也在 LS 平台的 Tables 视图中创建了同名表（DB、表名完全一致）
3. 用户点击「Pull from DB」按钮
4. 表单/文本模式自动同步字段（从 Doris 拉取最新的 schema）
5. 用户又在 Doris 中删除了 2 个字段并新增了 1 个字段
6. 再次点击「Pull from DB」→ 字段如实更新，与 Doris 表模式保持一致

**待定**

| 问题 | 状态 |
|------|------|
| 拉取数据格式：JSON（结构化字段列表）还是完整 CREATE TABLE 语句 | 未定 |
| 后端连接 Doris 的能力 | 待搭建（创建 Tables 时也需要拉取示例数据） |
| 「Pull from DB」按钮位置 | 未定，预计在表单/文本模式顶部工具栏 |

---

### 场景5：用户配置 Source/Sink 双用途表（草稿，表单 UI 待定）

**叙述**

1. 用户需要从 Kafka 读取数据，同时也需要向 Kafka 写入数据
2. 用户不希望在 Tables 中创建两张表（一张 Source、一张 Sink）
3. 系统提供三段式配置：**Common Config** / **Source Config** / **Sink Config**
4. 用户在同一个 DDL 中，Common 填写连接器通用属性（connector、topic、format），Source 填读取专属属性，Sink 填写入专属属性
5. 在 SQL 文件中引用该表时，`FROM` 走 Source 配置，`INSERT INTO` 走 Sink 配置，互不冲突

**关键决策**

- 不需要声明「这个表是 Source 还是 Sink」
- 三段配置全部写入 DDL，Flink 按读写语义自动选用

**DDL 结构示意**

```
CREATE TABLE kafka_order (
    order_no   STRING,
    user_id    BIGINT,
    pay_amt    DOUBLE
) WITH (
    -- Common
    'connector' = 'kafka',
    'topic' = 't_dwd_rw_ord_di',
    'format' = 'json',
    -- Source
    'scan.startup.mode' = 'earliest-offset',
    -- Sink
    'sink.partitioner' = 'round-robin'
);
```

所有三段配置最终都写入同一个 `WITH` 子句。Flink SQL 在 `FROM` 时自动使用 Source 相关属性，在 `INSERT INTO` 时自动使用 Sink 相关属性；不需要在 DDL 里显式声明 Source/Sink 角色。

**待定**

| 问题 | 状态 |
|------|------|
| 表单模式下三段配置的 UI 布局 | 未定（Common/Source/Sink 各一个属性表格，还是合并？） |

---

### 场景6：DDL 检查（已确认）

**叙述**

1. 用户在 LS Tables 中创建了一个 Doris 表，**手动**填写了字段
2. 点击「DDL 检查」按钮
3. 系统检查：
   - 表在 Doris 中是否存在
   - 字段是否一致（多了、少了）
   - 类型是否兼容
   - Table Properties 是否配置正确
4. 检查结果在 Terminal 中反馈给用户

**关键决策**

- 检查不阻断保存、提交、发布链路，仅是提示
- 和 S4「Pull from DB」共享同一套 Doris 连接基础设施
- 按钮位置：表单/文本模式顶部工具栏，与「Pull from DB」相邻

---

### 场景7：用户下线 Table（已确认）

**叙述**

1. 用户发现一个 Kafka Topic 已不再使用，需要下线对应的 Table `kfk_dwd_rw_report_di`，但线上还有任务 A 在引用
2. 用户删除该 Table，系统通过血缘提示有下游引用（血缘能力暂未实现，先描述场景）
3. 用户确认删除 → 系统物理删除 Table Index 记录
4. 其他用户在开发时**无法再引用该表**；但已提交/已发布的任务**不受影响**——它们使用的是当时任务提交/发布时的 Table 历史快照

**关键原则（新增）**

> **下游任务对表的依赖是对历史快照的依赖，不是对表当前状态的依赖。**
>
> - 表被删除、新增字段等变更，不影响已发布 Job 使用的历史快照
> - 只有重新提交 + 重新发布 Job，才会触发新的 Table 快照更新

---

## 业务流抽象

> 从 7 个场景中提取 5 条核心业务流。

### 流程 1：DDL 生命周期

```
创建 (S1) → 编辑 (S2/S3) → 保存到磁盘
                              ├── 删除 (S7) → Index 物理删除（开发不可引用，已发布 Job 不受影响）
                              └── 提交(git) → 入待发布池 → 发布 (S2)
```

**关键节点**：
- 创建：表单模式填写 → 确定 → 写盘 `tables/xxx.ddl`
- 编辑：两种模态——表单（确定保存）/ 文本（自动保存）
- 删除：物理删除磁盘文件 + Table Index 记录
- 提交 = git commit + 入待发布池（参考 `file-publish-design.md`）
- 发布：从待发布池选中 → 打包发布

**来源**：S1、S2、S3、S7

---

### 流程 2：双模态编辑与切换

```
打开 .ddl → 表单模式（编辑 → 确定保存）
               │ 序列化 DdlFormData（含未保存修改）
               ▼
            文本模式（编辑 → 1.5s 自动保存）
```

**关键节点**：
- 表单 → 文本：内存级序列化，未点确定的修改也带入 CodeMirror（S3）
- 文本 → 表单：解析 DDL 回填（后续 CP4）
- 表单无自动保存，文本有自动保存

**来源**：S1、S2、S3

---

### 流程 3：Table Index 同步

```
磁盘文件保存 → FileIndexService 解析 DDL
                 ├── 表名
                 ├── 连接器类型
                 ├── 字段列表（名+类型）
                 └── 写入 DB 索引 → SQL 编辑器代码提示

磁盘文件删除 → Table Index 物理删除 → 开发不可引用
                                     → 已发布 Job 仍用历史快照
```

**关键节点**：
- 触发时机：磁盘文件保存/删除时（不是 git commit/发布时）
- 索引是**磁盘级**，非 Git 级
- 删除即物理删除磁盘文件 + Table Index 记录（级联删除 Columns）

**来源**：S1、S2、S7

---

### 流程 4：外部数据源集成

```
                    Doris 连接器（待搭建）
                         │
          ┌──────────────┼──────────────┐
          ▼                              ▼
   Pull from DB (S4)              DDL 检查 (S6)
   拉取 schema → 同步到表单/文本     校验一致性 → Terminal 输出
```

**关键节点**：
- 共享同一套 Doris 连接基础设施
- Pull：拉取 schema → 回填表单或替换文本
- 检查：比对存在性/字段/类型/属性 → Terminal 反馈
- 检查不阻断任何链路（保存/提交/发布）

**来源**：S4、S6

---

### 流程 5：DDL 配置结构（三段式）

```
CREATE TABLE xxx ( 字段定义 )
WITH (
    Common Config  ← 连接器通用属性（connector、topic、format）
    Source Config  ← 读取专属（scan.startup.mode 等）
    Sink Config    ← 写入专属（sink.partitioner 等）
)
```

**关键节点**：
- 一张 DDL 同时支持读写，不区分 Source 表 / Sink 表
- FROM 走 Source 配置，INSERT INTO 走 Sink 配置
- 表单 UI：三段属性表格

**来源**：S5

---

### 核心原则（从场景 + 业务流提炼）

1. **Table Index 是磁盘文件索引，不是 Git 索引。** 保存到磁盘即触发更新，与 commit/发布解耦。（S1/S2）
2. **下游任务对表的依赖是对历史快照的依赖。** 删除/修改表不影响已发布 Job，只有重新提交+发布 Job 才触发新快照。（S7）

---

## 领域模型

> Table Index 是 DDL 磁盘文件的结构化缓存，数据权威始终在磁盘文件，Index 丢失可全量重建。

### lanting_table_index

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | INTEGER | 主键，自增 |
| `file_id` | INTEGER | 关联 FileIndexEntity.id（唯一） |
| `table_name` | VARCHAR(100) | CREATE TABLE 的表名 |
| `connector_type` | VARCHAR(50) | Kafka / Doris / HUDI / JDBC / FileSystem |
| `create_time` | BIGINT | 毫秒级时间戳 |
| `update_time` | BIGINT | 毫秒级时间戳 |

### lanting_column_index

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | INTEGER | 主键，自增 |
| `table_id` | INTEGER | 关联 lanting_table_index.id |
| `name` | VARCHAR(100) | 字段名 |
| `type` | VARCHAR(50) | STRING / BIGINT / DOUBLE / DATE / DATETIME / TIMESTAMP |
| `comment` | VARCHAR(500) | 字段注释，可为空 |
| `ordinal` | INTEGER | 字段排序（0-based） |
| `create_time` | BIGINT | 毫秒级时间戳 |
| `update_time` | BIGINT | 毫秒级时间戳 |

### 同步规则

```
Table 提交（Git commit / 发布）
  → 全量读取 Disk tables/ 目录下的 .ddl 文件
  → 逐个解析 DDL 文本（表名、连接器、字段列表）
  → 全量重建 lanting_table_index + lanting_column_index
    （先清空，再按当前磁盘状态重新插入）

文件保存 / 删除 / 重命名 / 移动
  → 不触发 Index 变更（Index 反映「已提交的表定义快照」）
```

### 数据流

```
磁盘 .ddl 文件 ←── 唯一数据源
       │
       ▼ （提交时全量解析重建）
Table Index (lanting_table_index + lanting_column_index)
       │
       ├── SQL 代码提示
       ├── 字段搜索
       └── DDL 检查（读盘解析 WITH 属性）
```

索引不存储 `WITH` 属性详情（Properties）。属性校验（S6）直接读取磁盘 DDL 解析，不依赖索引。

---

## API 设计

> **策略**：Table 文件（.ddl）的创建、保存、删除**全部走 FileController**（文件是 Disk 事实源，走通用文件接口）。
> **Table Index 只在「提交」时更新**（从 Disk 重新解析全量重建），不是保存时同步。
> TableController 只负责：DDL 的 deserialize/serialize（表单 ↔ 文本）、Index 查询、外部集成（check/pull）。

### 已有接口（复用，不改）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/files/tree?parentPath=tables/` | Tables 树数据 |
| POST | `/api/files/save` | 保存 .ddl（纯文件写盘，**不更新 Index**）|
| POST | `/api/files/create` | 创建 .ddl 文件 |
| DELETE | `/api/files/delete` | 删除 .ddl 文件（**不更新 Index**）|

> 文件 CRUD 由 FileController 统一处理，前端操作 .ddl 与 .sql 无差异（同一套接口）。

### TableController 接口

#### 1. 反序列化：DDL 文本 → 结构化表单数据

```
POST /api/tables/utils/deserialize
```

**请求**：
```json
{ "ddl": "CREATE TABLE IF NOT EXISTS ods_order (...)" }
```

**响应**（`Result<FlinkTable>`，字段对齐后端 model/FlinkTable）：
```json
{
  "code": 0, "message": "success",
  "data": {
    "tableName": "ods_order",
    "ifNotExists": true,
    "columns": [
      { "name": "order_id", "type": "BIGINT", "comment": "订单ID", "ordinal": 0, "columnType": "physical", "metadataFrom": null, "virtual": false, "expr": null },
      { "name": "kafka_partition", "type": "STRING", "comment": null, "ordinal": 6, "columnType": "metadata", "metadataFrom": "partition", "virtual": false, "expr": null },
      { "name": "gmv", "type": null, "comment": null, "ordinal": 7, "columnType": "computed", "metadataFrom": null, "virtual": false, "expr": "`amount` * (1 - `refund_rate`)" }
    ],
    "watermark": { "field": "create_time", "expr": "`create_time` - INTERVAL '5' SECOND" },
    "primaryKeys": ["order_id"],
    "comment": "订单流水表",
    "distribution": { "by": ["order_id"], "buckets": 4 },
    "partitionKeys": ["dt"],
    "properties": { "connector": "hudi" },
    "connector": "hudi"
  }
}
```

**实现**：Controller 直接调 `FlinkSqlParser.parseCreateTable(ddl)`（纯解析不落库）。

#### 2. 序列化：表单数据 → DDL 文本

```
POST /api/tables/utils/serialize
```

**请求**：`FlinkTable`（与 deserialize 响应同构）
**响应**：`Result<String>` — DDL 文本

**实现**：Controller 直接调 `FlinkSqlParser.createTableToString(form)`（纯生成不落库）。

#### 3. 提交（更新 Index）— 暂不实现

```
POST /api/tables/commit
```

- 从 Disk 的 tables/ 目录全量读取 .ddl 文件，逐个解析，**全量重建** `lanting_table_index` + `lanting_column_index`
- **触发时机：事件驱动（后续再说），本次不实现**——Index 同步挂到 Git 提交/发布事件上（与 file-publish 流程集成）
- 设计决策：Index 反映「已提交的表定义快照」，不是磁盘任意保存状态

#### 4. 查询全部表

```
GET /api/tables
```

**响应**（`Result<List<TableVO>>`）：
```json
{
  "code": 0,
  "message": "success",
  "data": [
    { "id": 1, "tableName": "ods_order", "connectorType": "HUDI", "fileId": 42 },
    { "id": 2, "tableName": "dws_rw_ord_cnt_1m", "connectorType": "Doris", "fileId": 55 }
  ]
}
```

#### 5. 按字段名搜索

```
GET /api/tables/search?q={keyword}
```

**响应**（`Result<List<TableSearchVO>>`）：
```json
{
  "code": 0,
  "message": "success",
  "data": [
    { "tableId": 1, "tableName": "ods_order", "columns": [{"name":"user_id","type":"BIGINT"}] },
    { "tableId": 3, "tableName": "kfk_user_log", "columns": [{"name":"user_id","type":"STRING"}] }
  ]
}
```

#### 6. DDL 检查（vs Doris，占位）

```
POST /api/tables/{tableId}/check
```

#### 7. 从 Datasource 拉取 Schema（占位）

```
POST /api/tables/pull
```

### 职责边界

```
FileController                    TableController
├── 文件 CRUD（创建/保存/删除）     ├── POST /utils/deserialize（DDL → FlinkTable）
├── 文件树（已有）                  ├── POST /utils/serialize（FlinkTable → DDL）
├── 锁管理（已有）                  ├── POST /commit（提交时重建 Index）
│                                 ├── GET /api/tables（Index 查询）
│                                 ├── GET /api/tables/search
│                                 ├── POST /api/tables/{id}/check（占位）
│                                 └── POST /api/tables/pull（占位）

Index 只在 commit 时全量重建（读 Disk tables/ 解析），
文件保存/删除不触发 Index 变更。
```

---

## 设计决策

### 存储

```
$workspace/
├── project/          ← SQL 文件根目录（受保护）
├── tables/          ← DDL 文件根目录（受保护）
│   ├── dws_rw_ord_cnt_1m.ddl
│   └── ...
└── .flink/       ← Flink 系统配置（隐藏 + 完全封禁）
    └── conf/
        ├── 42.json     ← fileId=42 的 SQL 文件的 Config
        └── 99.json
```

### 保护目录

| 目录 | 保护方式 | 说明 |
|---|---|---|
| `project/` | 目录本身不可删/改/移，内部文件正常 CRUD | SQL 文件根 |
| `tables/` | 目录本身不可删/改/移，内部文件正常 CRUD | DDL 文件根 |
| `.flink/` | 完全封禁（含所有子路径） | 系统配置，用户不可见 |

### 双模态编辑

| 模态 | 保存方式 | 适用场景 |
|---|---|---|
| 表单 | 显式「确定」按钮 | 快速建表、不熟悉 DDL |
| 文本 | 1.5s 自动保存 | 精细控制、CodeMirror 直接编辑 |

两种模态编辑同一底层 `.ddl` 文件，切换时双向同步。

### DDL 语法支持矩阵

`CREATE TABLE` 语句允许包含的子句（Flink 2.3 官方语法），以及表单编辑器对它们的支持计划：

```
CREATE TABLE [IF NOT EXISTS] [catalog.][db.]table_name
( { <physical> | <metadata> | <computed> }[ , ...n]
  [ <watermark> ] [ <table_constraint> ][ , ...n] )
[COMMENT table_comment]
[PARTITIONED BY (col1, col2, ...)]
[DISTRIBUTED BY (col) INTO n BUCKETS]
[LIKE source_table]
WITH ( 'key' = 'val', ... )
[AS select_query]          -- CTAS
```

| # | 子句/元素 | 语法 | 用途 | 支持计划 |
|---|----------|------|------|---------|
| 1 | **物理列** physical column | `col_name type [COMMENT '...']` | 普通字段（最基础） | ✅ 本次支持（字段表） |
| 2 | **计算列** computed column | `col_name AS expr` | 表达式派生列（如 `cost AS price*qty`） | ⏸ 短期不计划支持 |
| 3 | **元数据列** metadata column | `col_name type METADATA FROM 'alias' [VIRTUAL]` | 从消息头/元数据取 | ✅ 本次支持 |
| 4 | **WATERMARK** | `WATERMARK FOR ts AS expr` | 事件时间水位线 | ✅ 本次支持 |
| 5 | **表约束 PRIMARY KEY** | `PRIMARY KEY (col) NOT ENFORCED` | 主键（维度表/upsert 用）| ✅ 本次支持 |
| 6 | **COMMENT**（表级） | `COMMENT 'table desc'` | 表注释 | ✅ 本次支持 |
| 7 | **PARTITIONED BY** | `PARTITIONED BY (col)` | 分区（Hive/FileSystem）| ✅ 本次支持 |
| 8 | **DISTRIBUTED** | `DISTRIBUTED BY (col) INTO n BUCKETS` | 桶分布（新特性）| ✅ 本次支持 |
| 9 | **LIKE** | `LIKE source_table` | 复用其他表定义 | ❌ 长期不计划支持 |
| 10 | **WITH Options** | `'key' = 'val', ...` | 连接器配置 | ✅ 本次支持（key/value 表） |
| 11 | **AS select_query** (CTAS/RTAS) | `AS SELECT ...` | 建表即写入 | ❌ 长期不计划支持 |

#### 支持计划说明

1. **本次计划支持**：物理列、元数据列、WATERMARK、PRIMARY KEY、COMMENT、PARTITIONED BY、DISTRIBUTED、WITH Options
   - 结构化提取（后端 FlinkSqlParser），表单编辑 + serialize 还原
   - 计算列之外的所有常见 DDL 都能被表单完整表达，不丢语法
2. **短期不计划支持**：计算列（`col AS expr`）
   - 表达式是自由文本，表单化等于再造表达式编辑器，收益低
   - 打开含计算列的 DDL：表单只读展示计算列原文，提示「请用文本模式编辑」
3. **长期不计划支持**：LIKE、AS select_query（CTAS/RTAS）
   - 复用/写查询是另一形态（引用关系、SELECT 语句），不属于表定义表单的职责

> 设计原则：**表单绝不静默改写/丢失用户未在表单中编辑的语法**。遇到不支持的子句 → 只读展示原文 + 禁用表单保存（改 DDL 必须切文本模式）。

### ProjectPanel 多视图

| 视图 | 内容 | 数据源 |
|---|---|---|
| Project | SQL 文件 | `GET /files/tree` 过滤 |
| Tables | DDL 文件 | `GET /files/tree` 过滤 `tables/` |
| Changed | 未提交变更 | `GET /files/uncommit` |
| Workspace | 全量文件 | `GET /files/tree` 全量 |

四种视图共享同一 Tree 组件，通过过滤规则切换。
