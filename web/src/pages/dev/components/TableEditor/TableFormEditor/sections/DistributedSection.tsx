import { Card } from 'antd';
import { memo } from 'react';
import type { FlinkDistributionVO } from '@/types/table';

interface DistributedSectionProps {
  readonly?: boolean;
  distribution: FlinkDistributionVO | null;
}

const DistributedSection: React.FC<DistributedSectionProps> = ({ distribution }) => {
  return (
    <Card title="DISTRIBUTED BY" size="small" className="lt-form-section">
      <div className="lt-form-placeholder">
        {distribution
          ? `DISTRIBUTED BY (${distribution.by.join(', ')}) INTO ${distribution.buckets ?? '（未指定）'} BUCKETS`
          : '（未设置）'}
      </div>
    </Card>
  );
};

export default memo(DistributedSection);
