import React from 'react';
import { CodeOutlined } from '@ant-design/icons';

const HomePage: React.FC = () => {
  return (
    <div>
      {/* 页面标题 */}
      <div style={{ marginBottom: 18 }}>
        <div
          style={{
            fontSize: 16,
            fontWeight: 500,
            color: '#1A1915',
            fontFamily: 'Georgia, serif',
            marginBottom: 2,
          }}
        >
          概览
        </div>
        <div style={{ fontSize: 12, color: '#9B9689', fontFamily: 'system-ui' }}>
          今日 · default 工作空间
        </div>
      </div>

      {/* 快捷操作卡 */}
      <div
        style={{
          background: '#FAF9F6',
          border: '0.5px solid #E8E5DF',
          borderRadius: 10,
          padding: '10px 12px',
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          cursor: 'pointer',
        }}
      >
        <CodeOutlined style={{ fontSize: 15, color: '#D97757' }} />
        <div>
          <div style={{ fontSize: 12, fontWeight: 500, color: '#1A1915' }}>
            新建作业
          </div>
          <div style={{ fontSize: 10, color: '#9B9689', marginTop: 1 }}>
            AI 生成 SQL
          </div>
        </div>
      </div>
    </div>
  );
};

export default HomePage;
