import React, { useState } from 'react';
import { Layout, Menu, theme } from 'antd';
import { Outlet, useNavigate, useLocation, useIntl } from 'umi';
import type { MenuProps } from 'antd';
import {
  HomeOutlined,
  CodeOutlined,
  RocketOutlined,
  ToolOutlined,
  SafetyOutlined,
  ThunderboltOutlined,
  CloudServerOutlined,
} from '@ant-design/icons';
import LanguageSwitch from '@/components/LanguageSwitch';

const { Header, Content, Sider } = Layout;

type MenuItem = Required<MenuProps>['items'][number];

const getMenuItems = (formatMessage: ReturnType<typeof useIntl>['formatMessage']): MenuItem[] => [
  { key: '/', icon: <HomeOutlined />, label: formatMessage({ id: 'menu.home' }) },
  {
    key: 'dev',
    icon: <CodeOutlined />,
    label: formatMessage({ id: 'menu.dev' }),
    children: [
      { key: '/dev', label: formatMessage({ id: 'menu.task' }) },
      { key: '/datasource', label: formatMessage({ id: 'menu.datasource' }) },
    ],
  },
  { key: '/pub', icon: <RocketOutlined />, label: formatMessage({ id: 'menu.publish' }) },
  { key: '/ops', icon: <ToolOutlined />, label: formatMessage({ id: 'menu.ops' }) },
  { key: '/cluster', icon: <CloudServerOutlined />, label: formatMessage({ id: 'menu.cluster' }) },
  {
    key: 'auth',
    icon: <SafetyOutlined />,
    label: formatMessage({ id: 'menu.auth' }),
  },
];

const AppLayout: React.FC = () => {
  const [collapsed, setCollapsed] = useState(false);
  const nav = useNavigate();
  const location = useLocation();
  const { token } = theme.useToken();
  const { formatMessage } = useIntl();

  const selectedKey = location.pathname;
  const menuItems = getMenuItems(formatMessage);

  return (
      <Layout style={{ height: '100vh', overflow: 'hidden' }}>
        {/* 顶栏 */}
        <Header
          style={{
            height: 48,
            lineHeight: '48px',
            background: token.colorBgContainer,
            borderBottom: `0.5px solid ${token.colorBorder}`,
            display: 'flex',
            alignItems: 'center',
            padding: `0 ${token.sizeLG}px`,
            gap: token.sizeMD,
          }}
        >
          <div
            style={{
              width: 26,
              height: 26,
              borderRadius: token.borderRadius,
              background: token.colorPrimary,
              color: token.colorTextLightSolid,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: token.fontSizeSM,
              fontWeight: 500,
              fontFamily: 'var(--font-serif)',
              flexShrink: 0,
            }}
          >
            L
          </div>
          {/* 顶栏品牌名 */}
          <span style={{
            fontFamily: 'var(--font-serif)',
            fontSize: token.fontSize,
            fontWeight: 400,  // 400，不加粗
            color: token.colorText,
          }}>
            Lanting
          </span>
          <span
            style={{
              fontSize: token.fontSizeSM,
              color: token.colorTextDescription,
            }}
          >
            {formatMessage({ id: 'menu.userGroup' })}
          </span>
          <div style={{ marginLeft: 'auto' }}>
            <LanguageSwitch />
          </div>
        </Header>

        <Layout style={{ flex: 1, overflow: 'hidden' }}>
          {/* 侧边栏 */}
          <Sider
            width={180}
            collapsible
            collapsed={collapsed}
            onCollapse={setCollapsed}
            trigger={null}
            style={{
              background: token.colorBgContainer,
              borderRight: `0.5px solid ${token.colorBorder}`,
            }}
          >
            <div
              style={{
                display: 'flex',
                flexDirection: 'column',
                height: '100%',
              }}
            >
              <Menu
                mode="inline"
                selectedKeys={[selectedKey]}
                defaultOpenKeys={['dev', 'auth']}
                items={menuItems}
                onClick={({ key }) => nav(key)}
                style={{
                  flex: 1,
                  borderRight: 'none',
                  padding: `${token.sizeMD}px 0`,
                }}
              />
              {/* 用户区 */}
              <div
                style={{
                  padding: `${token.sizeMD}px ${token.sizeLG}px`,
                  borderTop: `0.5px solid ${token.colorBorder}`,
                  display: 'flex',
                  alignItems: 'center',
                  gap: token.sizeXS,
                  fontSize: token.fontSizeSM,
                  color: token.colorTextSecondary,
                }}
              >
                <div
                  style={{
                    width: 24,
                    height: 24,
                    borderRadius: '50%',
                    background: token.colorFillQuaternary,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: token.fontSizeSM,
                    color: token.colorTextDescription,
                  }}
                >
                  <ThunderboltOutlined />
                </div>
                {!collapsed && <span>Admin</span>}
              </div>
            </div>
          </Sider>

          {/* 内容区 */}
          <Content style={{
            flex: 1,
            overflowY: 'auto',
            overflowX: 'hidden',
            padding: `${token.sizeXL}px ${token.sizeLG}px`,
            background: token.colorBgContainer,  // 白色，不用 colorBgLayout
          }}>
            <Outlet />
          </Content>
        </Layout>
      </Layout>
  );
};

export default AppLayout;
