import React from 'react';
import { useNavigate } from 'umi';
import { ThunderboltOutlined } from '@ant-design/icons';

interface EntryCard {
  title: string;
  subtitle: string;
  icon: React.ReactNode;
  color: string;
  bg: string;
  path: string;
}

const cards: EntryCard[] = [
  {
    title: '任务开发',
    subtitle: 'SQL IDE',
    icon: '⚡',
    color: '#2A5CA0',
    bg: '#F0F5FC',
    path: '/editor',
  },
  {
    title: '数据源管理',
    subtitle: '数据集成',
    icon: '📦',
    color: '#2F9A45',
    bg: '#EDF7EF',
    path: '/datasource',
  },
  {
    title: '发布管理',
    subtitle: '持续交付',
    icon: '🚀',
    color: '#A97A1D',
    bg: '#FDF6EC',
    path: '/pub',
  },
  {
    title: '运维中心',
    subtitle: '可观测性',
    icon: '📊',
    color: '#9C2E24',
    bg: '#FCF0EF',
    path: '/ops',
  },
];

const HomePage: React.FC = () => {
  const navigate = useNavigate();

  return (
    <div>
      {/* 欢迎区 */}
      <div style={{ textAlign: 'center', padding: '48px 0 36px' }}>
        <div
          style={{
            width: 56,
            height: 56,
            borderRadius: 12,
            background: 'linear-gradient(135deg, #2A5CA0, #4A7CC0)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            margin: '0 auto 20px',
          }}
        >
          <ThunderboltOutlined style={{ fontSize: 28, color: '#fff' }} />
        </div>
        <h1
          style={{
            fontFamily: 'Georgia, "Noto Serif SC", serif',
            fontSize: 24,
            fontWeight: 500,
            color: '#111',
            margin: '0 0 8px',
          }}
        >
          欢迎来到 Lanting
        </h1>
        <p style={{ fontSize: 13, color: '#999', margin: 0, maxWidth: 440 }}>
          一站式实时数据开发平台，基于 Apache Flink 构建，让流处理开发更简单。
        </p>
      </div>

      {/* 入口卡片 */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(2, 1fr)',
          gap: 16,
          maxWidth: 560,
          margin: '0 auto',
        }}
      >
        {cards.map((card) => (
          <div
            key={card.title}
            onClick={() => navigate(card.path)}
            style={{
              background: '#FBFCFD',
              border: '0.5px solid #E5E5E5',
              borderRadius: 10,
              padding: '20px 22px',
              cursor: 'pointer',
              transition: 'border-color 0.2s, box-shadow 0.2s',
              display: 'flex',
              alignItems: 'center',
              gap: 14,
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.borderColor = '#CCC';
              e.currentTarget.style.boxShadow = '0 2px 8px rgba(0,0,0,0.06)';
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.borderColor = '#E5E5E5';
              e.currentTarget.style.boxShadow = 'none';
            }}
          >
            <div
              style={{
                width: 44,
                height: 44,
                borderRadius: 10,
                background: card.bg,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: 22,
                flexShrink: 0,
              }}
            >
              {card.icon}
            </div>
            <div>
              <div style={{ fontSize: 15, fontWeight: 600, color: '#111' }}>{card.title}</div>
              <div style={{ fontSize: 12, color: '#999', marginTop: 2 }}>{card.subtitle}</div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};

export default HomePage;
