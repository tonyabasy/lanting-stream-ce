/** 字段定义（与后端 ColumnVO 对齐） */
export interface ColumnVO {
  /** 字段名 */
  name: string;
  /** 字段类型（STRING / BIGINT / DOUBLE 等） */
  type: string;
  /** 字段注释 */
  comment?: string;
  /** 字段排序（0-based） */
  ordinal: number;
}

/** 表详情（与后端 TableVO 对齐） */
export interface TableVO {
  /** 表索引 ID */
  tableId: number;
  /** 关联文件 ID */
  fileId: number;
  /** CREATE TABLE 的表名 */
  tableName: string;
  /** 连接器类型（Kafka / Doris / JDBC 等） */
  connectorType: string;
  /** 分区字段名 */
  partitionField?: string;
  /** 字段列表 */
  columns?: ColumnVO[];
  /** 创建时间（毫秒时间戳） */
  createTime?: number;
  /** 更新时间（毫秒时间戳） */
  updateTime?: number;
}

// ── FlinkTable 模型（与后端 model/FlinkTable 对齐，deserialize/serialize 接口用）──

/** 列类型常量 */
export const COLUMN_TYPE = {
  PHYSICAL: 'physical',
  METADATA: 'metadata',
  COMPUTED: 'computed',
} as const;
export type ColumnType = typeof COLUMN_TYPE[keyof typeof COLUMN_TYPE];

/**
 * 列定义（physical / metadata / computed 统一模型，单 List 保持 DDL 顺序）。
 * - physical：name / type / comment / ordinal
 * - metadata：name / type / comment / ordinal / metadataFrom / virtual
 * - computed：name / expr / ordinal（无 type）
 */
export interface FlinkColumnVO {
  /** 列名（column_name） */
  name: string;
  /** 列类型（physical/metadata 有值；computed 为 null） */
  type: string | null;
  /** 列注释（column_comment） */
  comment: string | null;
  /** 列顺序（0-based，全局递增，保持 DDL 顺序） */
  ordinal: number;
  /** 列类型：physical / metadata / computed */
  columnType: ColumnType;
  /** 仅 metadata 列：METADATA FROM 'xxx' 的来源 */
  metadataFrom: string | null;
  /** 仅 metadata 列：是否 VIRTUAL */
  virtual: boolean;
  /** 仅 computed 列：计算表达式（AS 后部分） */
  expr: string | null;
}

/** 分布定义（DISTRIBUTED BY ... INTO n BUCKETS） */
export interface FlinkDistributionVO {
  /** 分布字段（bucket_column_name 列表） */
  by: string[];
  /** 桶数（INTO n BUCKETS，可能为 null） */
  buckets: number | null;
}

/** 水位线定义（WATERMARK FOR field AS expr） */
export interface FlinkWatermarkVO {
  /** 事件时间字段（rowtime_column_name） */
  field: string;
  /** 水位线策略表达式 */
  expr: string;
}

/**
 * FlinkTable（与后端 model/FlinkTable 对齐，字段顺序同官方 DDL 语法）。
 *
 * deserialize 响应 / serialize 请求共用此结构。
 */
export interface FlinkTableVO {
  /** 表名（table_name） */
  tableName: string;
  /** 是否 IF NOT EXISTS */
  ifNotExists: boolean;
  /** 列定义（physical/metadata/computed 交错，保持 DDL 顺序） */
  columns: FlinkColumnVO[];
  /** WATERMARK 定义 */
  watermark: FlinkWatermarkVO | null;
  /** PRIMARY KEY 约束字段 */
  primaryKeys: string[];
  /** 表级 COMMENT */
  comment: string | null;
  /** DISTRIBUTED BY 定义 */
  distribution: FlinkDistributionVO | null;
  /** PARTITIONED BY 分区字段 */
  partitionKeys: string[];
  /** WITH 连接器属性（含 connector） */
  properties: Record<string, string>;
  /** 连接器类型（从 WITH 的 connector 键提取） */
  connector: string | null;
}