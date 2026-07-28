import React, { useState } from 'react';
import { useNavigate, useSearchParams } from 'umi';
import { Button, Checkbox, Input, message, theme } from 'antd';
import { UserOutlined, LockOutlined } from '@ant-design/icons';
import request, { setToken } from '@/utils/request';

const LoginPage: React.FC = () => {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [remember, setRemember] = useState(false);
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { token } = theme.useToken();

  const handleLogin = async () => {
    if (!username || !password) return;
    setLoading(true);
    try {
      const data: any = await request.post('/auth/login', { username, password });
      if (data?.tokenInfo?.token) {
        setToken(data.tokenInfo.token);
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
    <>
      {/* 页面背景：中性浅灰，衬托白色卡片 */}
      <div style={{
        height: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: token.colorBgLayout,
      }}>
        {/* 登录卡片：白色，无边框，轻阴影，大圆角 */}
        <div style={{
          width: 400,
          background: token.colorBgContainer,
          borderRadius: token.borderRadiusLG,
          boxShadow: token.boxShadowTertiary,
          padding: `${token.sizeXXL}px ${token.sizeXL}px`,
        }}>

          {/* 品牌区：只有文字，Serif，无 Logo 图标 */}
          <div style={{ textAlign: 'center', marginBottom: token.sizeXXL }}>
            <div style={{
              fontFamily: 'var(--font-serif)',
              fontSize: token.fontSizeXL,
              fontWeight: 400,
              color: token.colorText,
              letterSpacing: '0.02em',
              marginBottom: token.sizeXXS,
            }}>
              Lanting Stream
            </div>
          </div>

          {/* 表单 */}
          <Input
            size="large"
            prefix={<UserOutlined style={{ color: token.colorTextDescription }} />}
            placeholder="用户名"
            value={username}
            onChange={e => setUsername(e.target.value)}
            onPressEnter={handleLogin}
            style={{ marginBottom: token.size, borderRadius: token.borderRadius }}
          />
          <Input.Password
            size="large"
            prefix={<LockOutlined style={{ color: token.colorTextDescription }} />}
            placeholder="密码"
            value={password}
            onChange={e => setPassword(e.target.value)}
            onPressEnter={handleLogin}
            style={{ marginBottom: token.size, borderRadius: token.borderRadius }}
          />

          {/* <div style={{ marginBottom: token.size }}>
            <Checkbox
              checked={remember}
              onChange={e => setRemember(e.target.checked)}
            >
              <span style={{
                fontSize: token.fontSizeCaption,
                color: token.colorTextSecondary,
              }}>
                记住我
              </span>
            </Checkbox>
          </div> */}

          <Button
            type="primary"
            block
            size="large"
            style={{borderRadius: token.borderRadius}}
            loading={loading}
            onClick={handleLogin}
          >
            登录
          </Button>
        </div>
      </div>
    </>
  );
};

export default LoginPage;