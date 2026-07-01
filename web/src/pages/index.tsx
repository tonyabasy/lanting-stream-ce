import React from 'react';

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
        <span style={{ fontSize: 15 }}>
          <CodeIcon />
        </span>
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

/**
 * 简易 Code 图标
 */
const CodeIcon: React.FC = () => (
  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="#D97757" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <polyline points="16 18 22 12 16 6" />
    <polyline points="8 6 2 12 8 18" />
  </svg>
);

export default HomePage;
