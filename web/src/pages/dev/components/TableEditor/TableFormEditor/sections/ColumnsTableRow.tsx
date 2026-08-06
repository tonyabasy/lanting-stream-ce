import { memo } from 'react';
import type { FormColumn } from '../hooks/useTableForm';

interface ColumnsTableRowProps {
  column: FormColumn;
  index: number;
  onUpdate: (id: string, patch: Partial<FormColumn>) => void;
  onRemove: (id: string) => void;
  onMove: (fromIndex: number, toIndex: number) => void;
}

const ColumnsTableRow: React.FC<ColumnsTableRowProps> = ({ column, index }) => {
  return (
    <div className="lt-columns-table-row">
      [{index}] {column.name || '（空）'} / {column.columnType}
    </div>
  );
};

export default memo(ColumnsTableRow);
