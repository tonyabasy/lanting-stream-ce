import { Card } from 'antd';
import { memo } from 'react';
import type { FormColumn } from '../hooks/useTableForm';

interface ColumnsSectionProps {
  readonly?: boolean;
  columns: FormColumn[];
  onAdd: (column?: Partial<FormColumn>) => void;
  onUpdate: (id: string, patch: Partial<FormColumn>) => void;
  onRemove: (id: string) => void;
  onMove: (fromIndex: number, toIndex: number) => void;
}

const ColumnsSection: React.FC<ColumnsSectionProps> = ({ readonly, columns, onAdd }) => {
  return (
    <Card title="字段定义" size="small" className="lt-form-section">
      <div className="lt-form-placeholder">
        共 {columns.length} 个字段
        <button type="button" disabled={readonly} onClick={() => onAdd()} style={{ marginLeft: 12 }}>
          添加占位
        </button>
      </div>
    </Card>
  );
};

export default memo(ColumnsSection);
