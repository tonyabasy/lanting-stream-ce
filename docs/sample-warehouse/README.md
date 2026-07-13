# Lanting Stream – Real-time Data Warehouse

基于 Flink + Hudi 的实时数仓项目。

## Architecture

| Layer | Storage | Description |
|-------|---------|-------------|
| ODS  | Kafka → Hudi | 原始日志接入 |
| DWD  | Hudi       | 清洗、去重、维表关联 |
| DWS  | Hudi       | 轻度聚合（日汇总） |
| ADS  | ClickHouse | 业务报表、即席查询 |

## Directory structure

```
├── ddl/         # 建表 DDL
├── config/      # Flink 作业配置
├── docs/        # 设计文档
└── sql/         # 数仓 ETL SQL
    ├── ods/     # 原始数据层
    ├── dwd/     # 明细数据层
    ├── dws/     # 汇总数据层
    └── ads/     # 应用数据层
```

## Quick start

```bash
# 启动 Flink 集群
./bin/flink cluster start

# 提交 ODS 作业
flink run -c com.lanting.stream.ods.OdsRunner app.jar
```
