import { Card } from 'antd';
import { memo } from 'react';
import { getConnectorTemplate } from '../constants/connectorTemplates';

interface ConnectorPropsSectionProps {
  readonly?: boolean;
  properties: Record<string, string>;
  connector: string | null;
}

const ConnectorPropsSection: React.FC<ConnectorPropsSectionProps> = ({ properties, connector }) => {
  const template = getConnectorTemplate(connector);
  const entries = Object.entries(properties);

  return (
    <Card title="连接器属性（WITH）" size="small" className="lt-form-section">
      <div className="lt-form-placeholder">
        连接器：{template?.label || connector || '（未选择）'}；属性数：{entries.length}
      </div>
    </Card>
  );
};

export default memo(ConnectorPropsSection);
