import React from 'react';
import { useModel } from 'umi';
import type { LantingToken } from '@/themes';

const TopBar: React.FC = () => {
  const t = useModel('theme') as LantingToken;

  return (
    <div
      style={{
        height: 40,
        display: 'flex',
        alignItems: 'center',
        flexShrink: 0,
      }}
    >
      TopBar
    </div>
  );
};

export default TopBar;
