import React from 'react';
import { useModel } from 'umi';
import type { LantingToken } from '@/themes';

const LoginPreview: React.FC = () => {
  const token = useModel('theme') as LantingToken;

  return (
    <>
      <style>{`
        .design-login *,
        .design-login *::before,
        .design-login *::after {
          box-sizing: border-box;
          margin: 0;
          padding: 0;
        }
        .design-login {
          font-family: ${token.fontFamily};
          background: ${token.colorBgLayout};
          color: ${token.colorText};
          min-height: 100%;
          display: flex;
          flex-direction: column;
          align-items: center;
          -webkit-font-smoothing: antialiased;
          padding: 0 16px;
        }
        .design-login .page-header {
          width: 100%;
          max-width: 440px;
          padding: 32px 0 16px;
        }
        .design-login .page-header h1 {
          font-family: 'Georgia', 'Noto Serif SC', serif;
          font-size: 18px;
          font-weight: 500;
          color: ${token.colorText};
        }
        .design-login .page-header .sub {
          font-size: 12px;
          color: ${token.colorTextDescription};
          margin-top: 2px;
        }
        .design-login .design-frame {
          width: 440px;
          background: ${token.colorBgLayout};
          padding: 80px 24px;
          display: flex;
          flex-direction: column;
          align-items: center;
          margin-bottom: 32px;
        }
        .design-login .login-card {
          width: 360px;
          background: ${token.colorBgContainer};
          border: 0.5px solid ${token.colorBorder};
          border-radius: ${token.borderRadiusLG}px;
          padding: 40px 32px 32px;
          display: flex;
          flex-direction: column;
          align-items: center;
          box-shadow: ${token.boxShadow};
        }
        .design-login .brand-title {
          font-family: 'Georgia', 'Noto Serif SC', serif;
          font-size: 22px;
          font-weight: 600;
          color: ${token.colorText};
          margin-bottom: 6px;
        }
        .design-login .brand-sub {
          font-size: 13px;
          font-weight: 400;
          color: ${token.colorTextDescription};
          margin-bottom: 32px;
        }
        .design-login .input-wrap {
          width: 100%;
          margin-bottom: 16px;
          position: relative;
        }
        .design-login .input-wrap:last-of-type {
          margin-bottom: 12px;
        }
        .design-login .input-field {
          width: 100%;
          height: 42px;
          border: 0.5px solid ${token.colorBorder};
          border-radius: ${token.borderRadiusSM}px;
          background: #FFFFFF;
          font-family: inherit;
          font-size: 14px;
          color: ${token.colorText};
          padding: 0 14px 0 38px;
          transition: border-color 0.2s, box-shadow 0.2s, background 0.2s;
          outline: none;
        }
        .design-login .input-field::placeholder {
          color: ${token.colorTextDisabled};
          font-size: 13px;
        }
        .design-login .input-field:hover {
          border-color: ${token.colorPrimary};
          background: #FAFBFC;
        }
        .design-login .input-field:focus {
          border-color: ${token.colorPrimary};
          box-shadow: 0 0 0 3px #2A5CA040;
        }
        .design-login .input-icon {
          position: absolute;
          left: 12px;
          top: 50%;
          transform: translateY(-50%);
          font-size: 15px;
          color: ${token.colorTextDescription};
          pointer-events: none;
        }
        .design-login .input-field:focus ~ .input-icon {
          color: ${token.colorPrimary};
        }
        .design-login .input-action {
          position: absolute;
          right: 10px;
          top: 50%;
          transform: translateY(-50%);
          font-size: 14px;
          color: ${token.colorTextDescription};
          cursor: pointer;
          user-select: none;
        }
        .design-login .input-action:hover {
          color: ${token.colorPrimary};
        }
        .design-login .remember-row {
          width: 100%;
          display: flex;
          align-items: center;
          gap: 8px;
          margin-bottom: 24px;
          font-size: 13px;
          color: ${token.colorTextSecondary};
        }
        .design-login .checkbox {
          appearance: none;
          width: 16px;
          height: 16px;
          border: 0.5px solid ${token.colorBorder};
          border-radius: 4px;
          background: #FFFFFF;
          cursor: pointer;
          position: relative;
          transition: all 0.15s;
          flex-shrink: 0;
        }
        .design-login .checkbox:checked {
          background: ${token.colorPrimary};
          border-color: ${token.colorPrimary};
        }
        .design-login .checkbox:checked::after {
          content: '✓';
          position: absolute;
          top: 0;
          left: 3px;
          font-size: 10px;
          font-weight: 700;
          color: #fff;
        }
        .design-login .checkbox:hover {
          border-color: ${token.colorPrimaryBorder};
        }
        .design-login .login-btn {
          width: 100%;
          height: 42px;
          border: none;
          border-radius: ${token.borderRadiusSM}px;
          background: ${token.colorPrimary};
          color: ${token.colorTextLightSolid};
          font-family: inherit;
          font-size: 15px;
          font-weight: 500;
          cursor: pointer;
          transition: background 0.2s, transform 0.1s;
        }
        .design-login .login-btn:hover {
          background: ${token.colorPrimaryHover};
        }
        .design-login .login-btn:active {
          background: #1C3B6B;
          transform: scale(0.98);
        }
        .design-login .toast {
          position: fixed;
          top: 28px;
          left: 50%;
          transform: translateX(-50%);
          background: #fff;
          border: 0.5px solid ${token.colorBorder};
          border-radius: ${token.borderRadiusSM}px;
          padding: 10px 18px;
          font-size: 13px;
          color: ${token.colorText};
          box-shadow: 0 4px 12px rgba(0,0,0,0.08);
          display: flex;
          align-items: center;
          gap: 8px;
          opacity: 0;
          pointer-events: none;
          transition: opacity 0.3s;
        }
        .design-login .palette {
          width: 440px;
          margin-bottom: 40px;
        }
        .design-login .palette h3 {
          font-family: 'Georgia', 'Noto Serif SC', serif;
          font-size: 14px;
          font-weight: 500;
          margin-bottom: 10px;
          color: ${token.colorText};
        }
        .design-login .palette-row {
          display: flex;
          gap: 8px;
          flex-wrap: wrap;
        }
        .design-login .palette-item {
          display: flex;
          align-items: center;
          gap: 8px;
          padding: 8px 12px;
          border-radius: ${token.borderRadiusSM}px;
          border: 0.5px solid ${token.colorBorder};
          background: #fff;
          font-size: 11px;
          color: ${token.colorTextSecondary};
        }
        .design-login .palette-dot {
          width: 14px;
          height: 14px;
          border-radius: 3px;
        }
        @media (max-width: 480px) {
          .design-login .design-frame { width: 100%; padding: 40px 16px; }
          .design-login .login-card { width: 100%; padding: 32px 20px 24px; }
          .design-login .page-header { padding: 20px 16px 12px; }
          .design-login .palette { width: 100%; padding: 0 16px; }
        }
      `}</style>

      <div className="design-login">
        <div className="page-header">
          <h1>Login 页面设计稿</h1>
          <div className="sub">Lanting Stream · 登录页面 · 宝蓝主题 {token.colorPrimary}</div>
        </div>

        <div className="design-frame">
          <div className="login-card">
            <div className="brand-title">Lanting</div>
            <div className="brand-sub">实时数据开发平台</div>
            <div className="input-wrap">
              <input className="input-field" type="text" placeholder="用户名" readOnly />
              <span className="input-icon">👤</span>
            </div>
            <div className="input-wrap">
              <input className="input-field" type="password" placeholder="密码" readOnly />
              <span className="input-icon">🔒</span>
              <span className="input-action">👁</span>
            </div>
            <div className="remember-row">
              <input className="checkbox" type="checkbox" readOnly />
              <label>记住我</label>
            </div>
            <button className="login-btn">登 录</button>
          </div>
        </div>

        <div className="palette">
          <h3>配色参考</h3>
          <div className="palette-row">
            <div className="palette-item">
              <div className="palette-dot" style={{ background: token.colorPrimary }} />
              <span>Primary {token.colorPrimary}</span>
            </div>
            <div className="palette-item">
              <div className="palette-dot" style={{ background: token.colorPrimaryHover }} />
              <span>Hover {token.colorPrimaryHover}</span>
            </div>
            <div className="palette-item">
              <div className="palette-dot" style={{ background: '#1C3B6B' }} />
              <span>Active #1C3B6B</span>
            </div>
            <div className="palette-item">
              <div className="palette-dot" style={{ background: token.colorBgLayout, border: `0.5px solid ${token.colorBorder}` }} />
              <span>Bg {token.colorBgLayout}</span>
            </div>
            <div className="palette-item">
              <div className="palette-dot" style={{ background: token.colorBgContainer, border: `0.5px solid ${token.colorBorder}` }} />
              <span>Card {token.colorBgContainer}</span>
            </div>
            <div className="palette-item">
              <div className="palette-dot" style={{ background: token.colorBorder }} />
              <span>Border {token.colorBorder}</span>
            </div>
            <div className="palette-item">
              <div className="palette-dot" style={{ background: token.colorText }} />
              <span>Text {token.colorText}</span>
            </div>
            <div className="palette-item">
              <div className="palette-dot" style={{ background: token.colorTextDescription }} />
              <span>Desc {token.colorTextDescription}</span>
            </div>
          </div>
        </div>
      </div>
    </>
  );
};

export default LoginPreview;
