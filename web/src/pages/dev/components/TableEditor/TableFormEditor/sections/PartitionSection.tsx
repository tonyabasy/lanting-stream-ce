import { Card } from 'antd';
import { memo } from 'react';
import { PARTITION_SUPPORTED_CONNECTORS } from '../constants/connectorTemplates';

interface PartitionSectionProps {
  readonly?: boolean;
  partitionKeys: string[];
  connector: string | null;
}

const PartitionSection: React.FC<PartitionSectionProps> = ({ partitionKeys, connector }) => {
  const supported = connector ? PARTITION_SUPPORTED_CONNECTORS.has(connector.toLowerCase()) : false;

  if (!supported) {
    return (
      <Card title="PARTITIONED BY" size="small" className="lt-form-section">
        <div className="lt-form-placeholder">当前连接器 {connector || '（未选择）'} 不支持分区</div>
      </Card>
    );
  }

  return (
    <Card title="PARTITIONED BY" size="small" className="lt-form-section">
      <div className="lt-form-placeholder">
        分区字段：{partitionKeys.length > 0 ? partitionKeys.join(', ') : '（未设置）'}
      </div>
    </Card>
  );
};

export default memo(PartitionSection);
