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
