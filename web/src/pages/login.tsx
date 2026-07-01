import React, { useState } from 'react';
import { useNavigate } from 'umi';
import { Button, Input, message } from 'antd';
import { UserOutlined, LockOutlined } from '@ant-design/icons';
import request, { setToken } from '@/utils/request';

const LoginPage: React.FC = () => {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const handleLogin = async () => {
    if (!username || !password) return;
    setLoading(true);
    try {
      const data: any = await request.post('/auth/login', { username, password });
      if (data?.tokenInfo?.token) {
        setToken(data.tokenInfo.token);
        navigate('/');
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
        background: '#FDFCF9',
      }}
    >
      <div
        style={{
          width: 360,
          background: '#FAF9F6',
          border: '0.5px solid #E8E5DF',
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
              background: '#D97757',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              margin: '0 auto 12px',
            }}
          >
            <span style={{ color: '#fff', fontSize: 18, fontWeight: 500 }}>L</span>
          </div>
          <div
            style={{
              fontSize: 16,
              fontWeight: 500,
              color: '#1A1915',
              fontFamily: 'Georgia, serif',
            }}
          >
            Lanting Stream
          </div>
          <div style={{ fontSize: 12, color: '#9B9689', marginTop: 4 }}>
            AI 驱动的 Flink SQL 平台
          </div>
        </div>

        {/* 表单 */}
        <Input
          size="large"
          prefix={<UserOutlined style={{ color: '#9B9689' }} />}
          placeholder="用户名"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          onPressEnter={handleLogin}
          style={{ marginBottom: 12 }}
        />
        <Input.Password
          size="large"
          prefix={<LockOutlined style={{ color: '#9B9689' }} />}
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
