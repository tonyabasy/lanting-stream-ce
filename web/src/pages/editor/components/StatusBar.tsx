import React from 'react';
import { useModel } from 'umi';
import type { LantingToken } from '@/themes';

const StatusBar: React.FC = () => {
  const t = useModel('theme') as LantingToken;

  return (
    <div
      style={{
        height: 22,
        display: 'flex',
        alignItems: 'center',
        flexShrink: 0,
        fontSize: 10,
        color: t.colorTextDescription,
        padding: `0 10px`,
      }}
    >
      StatusBar (22px 通栏)
    </div>
  );
};

export default StatusBar;
