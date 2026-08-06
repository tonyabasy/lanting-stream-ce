import { Card } from 'antd';
import { memo } from 'react';
import type { FormData } from '../hooks/useTableForm';

interface BasicInfoSectionProps {
  readonly?: boolean;
  formData: FormData;
  onChange: (patch: Partial<FormData>) => void;
}

const BasicInfoSection: React.FC<BasicInfoSectionProps> = ({ formData }) => {
  return (
    <Card title="基本信息" size="small" className="lt-form-section">
      <div className="lt-form-placeholder">
        表名：{formData.tableName || '（未填写）'} / 连接器：{formData.connector || '（未选择）'}
      </div>
    </Card>
  );
};

export default memo(BasicInfoSection);
