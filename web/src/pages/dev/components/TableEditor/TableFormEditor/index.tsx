import { useEffect } from 'react';
import { Alert, Button, Spin } from 'antd';
import { LoadingOutlined } from '@ant-design/icons';
import { useTableForm } from './hooks/useTableForm';
import BasicInfoSection from './sections/BasicInfoSection';
import ColumnsSection from './sections/ColumnsSection';
import PrimaryKeySection from './sections/PrimaryKeySection';
import WatermarkSection from './sections/WatermarkSection';
import DistributedSection from './sections/DistributedSection';
import PartitionSection from './sections/PartitionSection';
import ConnectorPropsSection from './sections/ConnectorPropsSection';
import './index.css';

export interface TableFormEditorProps {
  fileId: number;
  initialDdl: string;
  readonly?: boolean;
  onDirty?: (dirty: boolean) => void;
}

const TableFormEditor: React.FC<TableFormEditorProps> = ({
  initialDdl,
  readonly,
  onDirty,
}) => {
  const {
    formData,
    loading,
    error,
    setPartial,
    addColumn,
    updateColumn,
    removeColumn,
    moveColumn,
    serialize,
  } = useTableForm(initialDdl);

  // M1：任何 formData 变化都视为 dirty；后续可对比初始值精确判定
  useEffect(() => {
    onDirty?.(true);
  }, [formData, onDirty]);

  const handleSave = async () => {
    const ddl = await serialize();
    // TODO M5：调用 saveTable(fileId, ddl) 写盘并清 dirty
    // eslint-disable-next-line no-console
    console.log('[TableFormEditor] serialized DDL:', ddl);
  };

  if (loading) {
    return (
      <div className="lt-table-form-editor lt-table-form-editor--center">
        <Spin indicator={<LoadingOutlined spin />} />
      </div>
    );
  }

  return (
    <div className="lt-table-form-editor">
      {error && <Alert type="warning" description={error} banner closable />}

      <BasicInfoSection readonly={readonly} formData={formData} onChange={setPartial} />

      <ColumnsSection
        readonly={readonly}
        columns={formData.columns}
        onAdd={addColumn}
        onUpdate={updateColumn}
        onRemove={removeColumn}
        onMove={moveColumn}
      />

      <PrimaryKeySection readonly={readonly} primaryKeys={formData.primaryKeys} columns={formData.columns} />

      <WatermarkSection readonly={readonly} watermark={formData.watermark} />

      <DistributedSection readonly={readonly} distribution={formData.distribution} />

      <PartitionSection readonly={readonly} partitionKeys={formData.partitionKeys} connector={formData.connector} />

      <ConnectorPropsSection readonly={readonly} properties={formData.properties} connector={formData.connector} />

      {!readonly && (
        <div className="lt-table-form-footer">
          <Button onClick={handleSave}>确定</Button>
        </div>
      )}
    </div>
  );
};

export default TableFormEditor;
