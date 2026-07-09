import React from 'react';
import { useModel } from 'umi';
import type { LantingToken } from '@/themes';

const ConfigPanel: React.FC = () => {
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
      ConfigPanel（配置区）
    </div>
  );
};

export default ConfigPanel;
