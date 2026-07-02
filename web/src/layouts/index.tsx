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

const AppLayout: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const pathname = location.pathname;

  const handleLogout = () => {
    removeToken();
    navigate('/login');
  };

  const navItems = [
    { key: '/', icon: <HomeOutlined />, label: '概览' },
    { key: '/editor', icon: <CodeOutlined />, label: 'SQL 编辑器' },
    { key: '/datasource', icon: <DatabaseOutlined />, label: '数据源' },
    { key: '/cluster', icon: <ClusterOutlined />, label: '集群' },
    { key: '/users', icon: <UserOutlined />, label: '用户' },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100vh' }}>
      {/* 顶部导航 */}
      <div style={{
        height: 48,
        background: 'var(--color-bg-container)',
        borderBottom: '0.5px solid var(--color-border)',
        display: 'flex', alignItems: 'center',
        padding: '0 18px', gap: 12, flexShrink: 0,
      }}>
        <div style={{
          width: 26, height: 26, borderRadius: 6,
          background: 'var(--color-primary)',
          display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0,
        }}>
          <span style={{ color: 'var(--color-text-light-solid)', fontSize: 12, fontWeight: 500, fontFamily: 'var(--font-family)' }}>L</span>
        </div>
        <span style={{ fontSize: 14, fontWeight: 500, color: 'var(--color-text)', fontFamily: 'var(--font-family)' }}>
          Lanting Stream
        </span>
        <div style={{ flex: 1 }} />
        <LogoutOutlined
          style={{ fontSize: 16, color: 'var(--color-text-description)', cursor: 'pointer' }}
          onClick={handleLogout}
        />
      </div>

      <div style={{ display: 'flex', flex: 1, overflow: 'hidden' }}>
        {/* 侧边栏 */}
        <div style={{
          width: 48, background: 'var(--color-bg-subtle)',
          borderRight: '0.5px solid var(--color-border)',
          display: 'flex', flexDirection: 'column',
          alignItems: 'center', padding: '12px 0', gap: 2, flexShrink: 0,
        }}>
          {navItems.map((item) => {
            const active = pathname === item.key;
            return (
              <div
                key={item.key}
                onClick={() => navigate(item.key)}
                title={item.label}
                style={{
                  width: 32, height: 32, borderRadius: 6,
                  background: active ? 'var(--color-primary-bg)' : 'transparent',
                  color: active ? 'var(--color-primary)' : 'var(--color-text-description)',
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  cursor: 'pointer', fontSize: 15,
                  transition: 'background-color 0.15s, color 0.15s',
                }}
              >
                {item.icon}
              </div>
            );
          })}
        </div>

        {/* 内容区 */}
        <div style={{
          flex: 1, background: 'var(--color-bg-layout)',
          padding: '20px 22px', overflow: 'auto',
        }}>
          <Outlet />
        </div>
      </div>

    </div>
  );
};

export default AppLayout;
