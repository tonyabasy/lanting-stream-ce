# 后端 Table parse 实现计划

> 2026-08-04 · 前置：`docs/backend/editor-tables-design.md`（API 设计 #0 parse 接口 + 字段核对表）、`docs/plan/editor-panel-router-plan.md`（前端双模态）、`docs/frontend/design/table-editor-design.html`（表单五段式）。

## 目标

按 DDL 支持矩阵扩展 `FlinkSqlParser` 提取全部字段（physical/metadata/computed/watermark/PK/COMMENT/partition/DISTRIBUTED/WITH），新增 `POST /api/tables/parse` 供前端表单数据源，对齐 `editor-tables-design.md` API 设计 #0。

## 改动清单

### 1. `util/FlinkSqlParser.java`（改）— parser 扩展

- `Table` record 扩展字段：

```java
record Table(
    String tableName,
    boolean ifNotExists,
    Map<String, String> properties,
    String connector,
    List<String> partitionKeys,
    List<Column> columns,                 // physical
    List<Column> metadataColumns,         // 🆕 metadata（name/type/comment/ordinal + alias/virtual）
    List<ComputedColumn> computedColumns, // 🆕 computed（name/expr）
    String comment,                       // 🆕 表级 COMMENT
    List<String> primaryKeys,             // 🆕 getFullConstraints() → isPrimaryKey
    Distribution distribution,            // 🆕 by + buckets
    Watermark watermark                   // 🆕 field + expr
)
```

- 新增子 record：`ComputedColumn(name, expr)`、`Distribution(by, buckets)`、`Watermark(field, expr)`
- `Column` 扩展：加 `metadataFrom` / `virtual`（metadata 列专用，physical 为 null/false）——**或分开存**（实现时定：倾向 Column 统一带 optional 字段，避免三个 list 类型分裂）
- `extractColumns()` 按 instanceof 分流：regular→columns / metadata→metadataColumns / computed→computedColumns
- 提取 comment：`createTable.getComment()`
- 提取主键：`getFullConstraints()` 过滤 `isPrimaryKey()` → `getColumnNames()`
- 提取 distribution：`getDistribution()` → getBucketColumns / getBucketCount（`getValueAs(Long.class)`）
- 提取 watermark：`getWatermark()` → getEventTimeColumnName / getWatermarkStrategy（SqlNode.toString）
- **所有新增提取 null-safe**，不影响现有调用点（`TableService.updateIndex` 只用 tableName/connector/partitionKeys/columns）

### 2. `vo/TableParseVO.java`（新）— parse 响应 VO

```java
record TableParseVO(
    String tableName, boolean ifNotExists, String comment,
    String connector, List<String> partitionKeys,
    DistributionVO distribution, List<String> primaryKeys,
    WatermarkVO watermark, Map<String, String> properties,
    List<ColumnParseVO> columns, List<String> parseWarnings
)
```

- `ColumnParseVO(name, type, comment, columnType, metadataFrom, virtual, expr)`
  - `columnType: physical|metadata|computed` → 前端字段表徽标来源
- `DistributionVO(by, buckets)`、`WatermarkVO(field, expr)`

### 3. `dto/ParseDdlRequest.java`（新）— 请求体 `{ ddl: String }`

### 4. `service/TableService.java`（改）— 新增 parseDdl

```java
public TableParseVO parseDdl(String ddl) {
    // FlinkSqlParser.parseCreateTable(ddl) → TableParseVO
    // 异常 → parseWarnings=["无法解析，请用文本模式编辑"] + 空结构
}
```

### 5. `controller/TableController.java`（改）— 新增 /parse

```java
@PostMapping("/parse")
public Result<TableParseVO> parse(@RequestBody @Valid ParseDdlRequest request)
```

### 6. 测试

- `FlinkSqlParserTest`（改）：新增用例——metadata / computed / watermark / PK / comment / distributed / 混合 DDL
- `TableControllerIntegrationTest`（改）：POST /api/tables/parse 成功 + 失败（非 CREATE TABLE → parseWarnings）
- `TableServiceTest`（改）：parseDdl 正常 + 解析失败兜底

## 实施步骤（每步停顿 review）

| Step | 内容 | 产出 | 验证 |
|------|------|------|------|
| 1 | FlinkSqlParser 扩展（record + extract） | FlinkSqlParser.java | 单测（含混合 DDL）|
| 2 | TableParseVO + ColumnParseVO + DTO | vo/dto 文件 | 编译 |
| 3 | TableService.parseDdl + TableController /parse | 2 文件 | 集成测试 |
| 4 | 全量测试 + mvn compile | — | `mvn test` |

## 明确不做（本次）

- TableVO / Index 结构不动（Index 仍是查询层，parse 是独立能力）
- check / pull 占位接口不实现
- 前端对接（下一步）
