/**
 * 连接器模板配置。
 *
 * 用于：
 * - 切换连接器时预填推荐属性 key
 * - WITH 属性表分组（Common / Source / Sink）
 */
export interface ConnectorTemplate {
  label: string;
  /** 通用属性 key 列表 */
  commonKeys: string[];
  /** Source 侧属性 key 列表 */
  sourceKeys: string[];
  /** Sink 侧属性 key 列表 */
  sinkKeys: string[];
  /** 默认属性（切换连接器时自动填入） */
  defaultProps: Record<string, string>;
}

export const CONNECTOR_TEMPLATES: Record<string, ConnectorTemplate> = {
  kafka: {
    label: 'Kafka',
    commonKeys: ['connector', 'topic', 'format', 'properties.bootstrap.servers'],
    sourceKeys: ['scan.startup.mode', 'scan.startup.specific-offsets', 'scan.bounded.mode'],
    sinkKeys: ['sink.partitioner', 'sink.semantic', 'sink.buffer-flush.max-rows', 'sink.buffer-flush.interval'],
    defaultProps: { connector: 'kafka' },
  },
  'upsert-kafka': {
    label: 'Upsert Kafka',
    commonKeys: ['connector', 'topic', 'properties.bootstrap.servers', 'key.format', 'value.format'],
    sourceKeys: ['scan.startup.mode'],
    sinkKeys: ['sink.buffer-flush.max-rows', 'sink.buffer-flush.interval'],
    defaultProps: { connector: 'upsert-kafka' },
  },
  jdbc: {
    label: 'JDBC',
    commonKeys: ['connector', 'url', 'table-name', 'username', 'password', 'driver'],
    sourceKeys: ['scan.partition.column', 'scan.partition.num', 'scan.partition.lower-bound', 'scan.partition.upper-bound'],
    sinkKeys: ['sink.buffer-flush.max-rows', 'sink.buffer-flush.interval', 'sink.max-retries'],
    defaultProps: { connector: 'jdbc' },
  },
  filesystem: {
    label: 'FileSystem',
    commonKeys: ['connector', 'path', 'format'],
    sourceKeys: ['source.monitor-interval', 'source.path-regex-pattern'],
    sinkKeys: ['sink.partition-commit.trigger', 'sink.partition-commit.delay', 'sink.partition-commit.policy.kind'],
    defaultProps: { connector: 'filesystem' },
  },
  hive: {
    label: 'Hive',
    commonKeys: ['connector', 'hive-conf-dir', 'hive-database', 'hive-table'],
    sourceKeys: [],
    sinkKeys: ['sink.partition-commit.trigger', 'sink.partition-commit.delay'],
    defaultProps: { connector: 'hive' },
  },
};

export const getConnectorTemplate = (connector: string | null): ConnectorTemplate | undefined => {
  if (!connector) return undefined;
  return CONNECTOR_TEMPLATES[connector.toLowerCase()];
};

/** 支持分区的连接器白名单 */
export const PARTITION_SUPPORTED_CONNECTORS = new Set(['hive', 'filesystem']);
