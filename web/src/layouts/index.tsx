import React, { useState } from 'react';
import { ConfigProvider, Layout, Menu } from 'antd';
import { Outlet, useNavigate, useLocation, useModel, useIntl } from 'umi';
import type { MenuProps } from 'antd';
import {
  HomeOutlined,
  CodeOutlined,
  RocketOutlined,
  ToolOutlined,
  EyeOutlined,
  SafetyOutlined,
  ThunderboltOutlined,
  CloudServerOutlined,
} from '@ant-design/icons';
import type { LantingToken } from '@/themes';
import {toAntdTheme} from '@/themes';
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
      { key: '/editor', label: formatMessage({ id: 'menu.task' }) },
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
  {
    key: 'design',
    icon: <EyeOutlined />,
    label: formatMessage({ id: 'menu.design' }),
    children: [
      { key: '/design/login', label: formatMessage({ id: 'menu.design.login' }) },
      { key: '/design/cluster', label: formatMessage({ id: 'menu.design.cluster' }) },
      { key: '/design/editor', label: formatMessage({ id: 'menu.design.editor' }) },
      { key: '/design/theme-preview', label: formatMessage({ id: 'menu.design.theme' }) },
    ],
  },
];

const AppLayout: React.FC = () => {
  const [collapsed, setCollapsed] = useState(false);
  const nav = useNavigate();
  const location = useLocation();
  const token = useModel('theme') as LantingToken;
  const { formatMessage } = useIntl();

  const selectedKey = location.pathname;
  const menuItems = getMenuItems(formatMessage);

  return (
    <ConfigProvider theme={toAntdTheme(token)}>
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
              fontSize: token.fontSizeCaption,
              fontWeight: token.fontWeightMedium,
              fontFamily: 'var(--font-serif)',
              flexShrink: 0,
            }}
          >
            L
          </div>
          {/* 顶栏品牌名 */}
          <span style={{
            fontFamily: 'var(--font-serif)',
            fontSize: token.fontSizeBody,
            fontWeight: token.fontWeightRegular,  // 400，不加粗
            color: token.colorText,
          }}>
            Lanting
          </span>
          <span
            style={{
              fontSize: token.fontSizeCaption,
              color: token.colorTextDescription,
            }}
          >
            {formatMessage({ id: 'menu.userGroup' })}
          </span>
          <div style={{ marginLeft: 'auto' }}>
            <LanguageSwitch token={token} />
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
                defaultOpenKeys={['dev', 'auth', 'design']}
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
                  gap: token.sizeSM,
                  fontSize: token.fontSizeCaption,
                  color: token.colorTextSecondary,
                }}
              >
                <div
                  style={{
                    width: 24,
                    height: 24,
                    borderRadius: '50%',
                    background: token.colorBgSubtle,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: token.fontSizeCaption,
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
            padding: `${token.sizeXL}px ${token.size2XL}px`,
            background: token.colorBgContainer,  // 白色，不用 colorBgLayout
          }}>
            <Outlet />
          </Content>
        </Layout>
      </Layout>
    </ConfigProvider>
  );
};

export default AppLayout;
