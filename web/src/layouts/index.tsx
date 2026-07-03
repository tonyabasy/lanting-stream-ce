import React, { useState } from 'react';
import { ConfigProvider, Layout, Menu } from 'antd';
import { Outlet, useNavigate, useLocation, useModel } from 'umi';
import type { MenuProps } from 'antd';
import {
  HomeOutlined,
  CodeOutlined,
  RocketOutlined,
  ToolOutlined,
  EyeOutlined,
  SafetyOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import type { LantingToken } from '@/themes/parseTheme';
import {toAntdTheme} from '@/themes/parseTheme';

const { Header, Content, Sider } = Layout;

type MenuItem = Required<MenuProps>['items'][number];

const menuItems: MenuItem[] = [
  { key: '/', icon: <HomeOutlined />, label: '首页' },
  {
    key: 'dev',
    icon: <CodeOutlined />,
    label: '研发',
    children: [
      { key: '/editor', label: '任务开发' },
      { key: '/datasource', label: '数据源' },
    ],
  },
  { key: '/pub', icon: <RocketOutlined />, label: '发布' },
  { key: '/ops', icon: <ToolOutlined />, label: '运维' },
  {
    key: 'auth',
    icon: <SafetyOutlined />,
    label: '权限',
  },
  {
    key: 'design',
    icon: <EyeOutlined />,
    label: '设计稿',
    children: [
      { key: '/design/login', label: '登录页' },
      { key: '/design/cluster', label: '集群管理' },
      { key: '/design/editor', label: '编辑器' },
      { key: '/design/theme-preview', label: '主题预览' },
    ],
  },
];

const AppLayout: React.FC = () => {
  const [collapsed, setCollapsed] = useState(false);
  const nav = useNavigate();
  const location = useLocation();
  const token = useModel('theme') as LantingToken;

  const selectedKey = location.pathname;

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
            padding: `0 ${token.spacingLG}px`,
            gap: token.spacingMD,
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
            暂无用户组
          </span>
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
                  padding: `${token.spacingMD}px 0`,
                }}
              />
              {/* 用户区 */}
              <div
                style={{
                  padding: `${token.spacingMD}px ${token.spacingLG}px`,
                  borderTop: `0.5px solid ${token.colorBorder}`,
                  display: 'flex',
                  alignItems: 'center',
                  gap: token.spacingSM,
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
            padding: `${token.spacingXL}px ${token.spacing2XL}px`,
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
