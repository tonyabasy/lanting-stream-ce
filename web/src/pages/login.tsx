import React, { useState } from 'react';
import { useNavigate, useSearchParams, useModel } from 'umi';
import { Button, Checkbox, ConfigProvider, Input, message } from 'antd';
import { UserOutlined, LockOutlined } from '@ant-design/icons';
import request, { setToken } from '@/utils/request';
import { toAntdTheme } from '@/themes/parseTheme';
import type { LantingToken } from '@/themes/parseTheme';

const LoginPage: React.FC = () => {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [remember, setRemember] = useState(false);
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const token = useModel('theme') as LantingToken;

  const handleLogin = async () => {
    if (!username || !password) return;
    setLoading(true);
    try {
      const data: any = await request.post('/auth/login', { username, password });
      if (data?.tokenInfo?.token) {
        setToken(data.tokenInfo.token);
        // 登录成功后跳回过期前的页面，没有 redirect 参数则回首页
        const redirect = searchParams.get('redirect') || '/';
        navigate(decodeURIComponent(redirect));
      }
    } catch (e: any) {
      message.error(e.message || '登录失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <ConfigProvider theme={toAntdTheme(token)}>
      <div
        style={{
          height: '100vh',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          background: token.colorBgLayout,
        }}
      >
        <div
          style={{
            width: 450,
            background: token.colorBgContainer,
            border: `0.5px solid ${token.colorBorder}`,
            borderRadius: token.borderRadiusLG,
            padding: `${token.spacing4XL}px ${token.spacing2XL}px`,
          }}
        >
          {/* Logo + 标题 */}
          <div style={{ textAlign: 'center', marginBottom: token.spacing3XL }}>
            <div
              style={{
                width: 40,
                height: 40,
                borderRadius: token.borderRadius,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                margin: `0 auto ${token.spacingMD}px`,
                fontSize: token.fontSizeHeading,
                fontWeight: token.fontWeightMedium,
                fontFamily: 'var(--font-serif)',
              }}
            >
              LantingStream
            </div>
            <div style={{ fontSize: token.fontSizeCaption, color: token.colorTextDescription, marginTop: token.spacingXS }}>
              实时数据开发平台
            </div>
          </div>

          {/* 表单 */}
          <Input
            size="large"
            prefix={<UserOutlined style={{ color: token.colorTextDescription }} />}
            placeholder="用户名"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            onPressEnter={handleLogin}
            style={{ marginBottom: token.spacingMD }}
          />
          <Input.Password
            size="large"
            prefix={<LockOutlined style={{ color: token.colorTextDescription }} />}
            placeholder="密码"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            onPressEnter={handleLogin}
            style={{ marginBottom: token.spacingMD }}
          />
          <div style={{ marginBottom: token.spacing2XL }}>
            <Checkbox checked={remember} onChange={(e) => setRemember(e.target.checked)}>
              <span style={{ fontSize: token.fontSizeCaption, color: token.colorTextSecondary }}>记住我</span>
            </Checkbox>
          </div>
          <Button
            type="primary"
            block
            size="large"
            loading={loading}
            onClick={handleLogin}
          >
            登录
          </Button>
        </div>
      </div>
    </ConfigProvider>
  );
};

export default LoginPage;
