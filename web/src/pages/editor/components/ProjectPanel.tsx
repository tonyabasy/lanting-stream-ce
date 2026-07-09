import React from 'react';
import { useModel } from 'umi';
import type { LantingToken } from '@/themes';

const ProjectPanel: React.FC = () => {
  const t = useModel('theme') as LantingToken;

  return (
    <div
      style={{
        height: '100%',
        background: t.colorBgContainer,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        fontSize: t.fontSizeBody,
        color: t.colorTextDescription,
      }}
    >
      ProjectPanel（目录区）
    </div>
  );
};

export default ProjectPanel;
