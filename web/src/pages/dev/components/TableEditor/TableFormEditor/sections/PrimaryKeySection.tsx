import { Card } from 'antd';
import { memo } from 'react';
import type { FormColumn } from '../hooks/useTableForm';

interface PrimaryKeySectionProps {
  readonly?: boolean;
  primaryKeys: string[];
  columns: FormColumn[];
}

const PrimaryKeySection: React.FC<PrimaryKeySectionProps> = ({ primaryKeys, columns }) => {
  return (
    <Card title="PRIMARY KEY" size="small" className="lt-form-section">
      <div className="lt-form-placeholder">
        主键字段：{primaryKeys.length > 0 ? primaryKeys.join(', ') : '（未设置）'}
        （候选列：{columns.filter((c) => c.columnType === 'physical').map((c) => c.name).join(', ') || '无'}）
      </div>
    </Card>
  );
};

export default memo(PrimaryKeySection);
