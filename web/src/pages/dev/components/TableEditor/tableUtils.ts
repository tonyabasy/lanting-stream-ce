/**
 * Table 表单模型工具（当前为空壳）。
 *
 * 序列化/反序列化（DDL 文本 ↔ 结构化）由后端承担（后端才有 Flink 原生 parse 能力）：
 * - deserialize：POST /api/tables/utils/deserialize（DDL 文本 → FlinkTableVO）
 * - serialize：POST /api/tables/utils/serialize（FlinkTableVO → DDL 文本）
 *
 * 前端表单模型 TableFormData 与 FlinkTableVO 结构一致（properties 同为 Record），
 * 类型继承即可，无需转换函数。本文件保留为后续表单工具扩展位。
 */
