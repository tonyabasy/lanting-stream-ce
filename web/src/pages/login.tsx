import React, { useState } from 'react';
import { useNavigate, useSearchParams } from 'umi';
import { Button, Input, message } from 'antd';
import { UserOutlined, LockOutlined } from '@ant-design/icons';
import request, { setToken } from '@/utils/request';

const LoginPage: React.FC = () => {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

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
    <div
      style={{
        height: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'var(--color-bg-layout)',
      }}
    >
      <div
        style={{
          width: 360,
          background: 'var(--color-bg-container)',
          border: '0.5px solid var(--color-border)',
          borderRadius: 10,
          padding: '40px 32px',
        }}
      >
        {/* Logo + 标题 */}
        <div style={{ textAlign: 'center', marginBottom: 32 }}>
          <div
            style={{
              width: 40,
              height: 40,
              borderRadius: 6,
              background: 'var(--color-primary)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              margin: '0 auto 12px',
            }}
          >
            <span style={{ color: 'var(--color-text-light-solid)', fontSize: 18, fontWeight: 500, fontFamily: 'var(--font-family)' }}>L</span>
          </div>
          <div
            style={{
              fontSize: 16,
              fontWeight: 500,
              color: 'var(--color-text)',
              fontFamily: 'var(--font-family)',
            }}
          >
            Lanting Stream
          </div>
          <div style={{ fontSize: 12, color: 'var(--color-text-description)', marginTop: 4 }}>
            AI 驱动的 Flink SQL 平台
          </div>
        </div>

        {/* 表单 */}
        <Input
          size="large"
          prefix={<UserOutlined style={{ color: 'var(--color-text-description)' }} />}
          placeholder="用户名"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          onPressEnter={handleLogin}
          style={{ marginBottom: 12 }}
        />
        <Input.Password
          size="large"
          prefix={<LockOutlined style={{ color: 'var(--color-text-description)' }} />}
          placeholder="密码"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          onPressEnter={handleLogin}
          style={{ marginBottom: 24 }}
        />
        <Button
          type="primary"
          block
          size="large"
          loading={loading}
          disabled={!username || !password}
          onClick={handleLogin}
        >
          登录
        </Button>
      </div>
    </div>
  );
};

export default LoginPage;
