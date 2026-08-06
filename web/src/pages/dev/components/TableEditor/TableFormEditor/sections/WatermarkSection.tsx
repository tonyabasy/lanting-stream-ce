import { Card } from 'antd';
import { memo } from 'react';
import type { FlinkWatermarkVO } from '@/types/table';

interface WatermarkSectionProps {
  readonly?: boolean;
  watermark: FlinkWatermarkVO | null;
}

const WatermarkSection: React.FC<WatermarkSectionProps> = ({ watermark }) => {
  return (
    <Card title="WATERMARK" size="small" className="lt-form-section">
      <div className="lt-form-placeholder">
        {watermark
          ? `WATERMARK FOR ${watermark.field} AS ${watermark.expr}`
          : '（未设置）'}
      </div>
    </Card>
  );
};

export default memo(WatermarkSection);
