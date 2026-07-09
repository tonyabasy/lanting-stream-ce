import React from 'react';
import { useModel } from 'umi';
import type { LantingToken } from '@/themes';

const LeftSidebar: React.FC = () => {
  const t = useModel('theme') as LantingToken;

  return (
    <div
      style={{
        width: 20,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        flexShrink: 0,
        fontSize: t.fontSizeCaption,
        color: t.colorTextDescription,
        writingMode: 'vertical-rl',
        padding: '4px 0',
      }}
    >
      左侧边栏
    </div>
  );
};

export default LeftSidebar;
