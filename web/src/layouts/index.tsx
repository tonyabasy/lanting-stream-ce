import React from 'react';
import { Outlet, useNavigate, useLocation } from 'umi';
import {
  HomeOutlined,
  CodeOutlined,
  DatabaseOutlined,
  ClusterOutlined,
  UserOutlined,
  LogoutOutlined,
} from '@ant-design/icons';
import { removeToken } from '@/utils/request';

/**
 * 主布局：claude.ai 风格 — 顶部导航米白 + 侧边栏 48px 图标
 */
const AppLayout: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const pathname = location.pathname;

  const handleLogout = () => {
    removeToken();
    navigate('/login');
  };

  const navItems = [
    { key: '/', icon: <HomeOutlined /> },
    { key: '/jobs', icon: <CodeOutlined /> },
    { key: '/datasource', icon: <DatabaseOutlined /> },
    { key: '/cluster', icon: <ClusterOutlined /> },
    { key: '/users', icon: <UserOutlined /> },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100vh' }}>
      {/* 顶部导航 */}
      <div
        style={{
          height: 48,
          background: '#FAF9F6',
          borderBottom: '0.5px solid #E8E5DF',
          display: 'flex',
          alignItems: 'center',
          padding: '0 18px',
          gap: 12,
          flexShrink: 0,
        }}
      >
        {/* Logo */}
        <div
          style={{
            width: 26,
            height: 26,
            borderRadius: 6,
            background: '#D97757',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexShrink: 0,
          }}
        >
          <span style={{ color: '#fff', fontSize: 12, fontWeight: 500 }}>L</span>
        </div>
        {/* 产品名 */}
        <span
          style={{
            fontSize: 14,
            fontWeight: 500,
            color: '#1A1915',
            fontFamily: 'Georgia, serif',
          }}
        >
          Lanting Stream
        </span>
        <div style={{ flex: 1 }} />
        <LogoutOutlined
          style={{ fontSize: 16, color: '#9B9689', cursor: 'pointer' }}
          onClick={handleLogout}
        />
      </div>

      <div style={{ display: 'flex', flex: 1, overflow: 'hidden' }}>
        {/* 侧边栏 */}
        <div
          style={{
            width: 48,
            background: '#F5F3EE',
            borderRight: '0.5px solid #E8E5DF',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            padding: '12px 0',
            gap: 2,
            flexShrink: 0,
          }}
        >
          {navItems.map((item) => {
            const active = pathname === item.key;
            return (
              <div
                key={item.key}
                onClick={() => navigate(item.key)}
                style={{
                  width: 32,
                  height: 32,
                  borderRadius: 6,
                  background: active ? '#FDEEE8' : 'transparent',
                  color: active ? '#D97757' : '#9B9689',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  cursor: 'pointer',
                  fontSize: 15,
                  transition: 'background-color 0.15s ease, color 0.15s ease',
                }}
              >
                {item.icon}
              </div>
            );
          })}
        </div>

        {/* 内容区 */}
        <div
          style={{
            flex: 1,
            background: '#FDFCF9',
            padding: '20px 22px',
            overflow: 'auto',
          }}
        >
          <Outlet />
        </div>
      </div>
    </div>
  );
};

export default AppLayout;
