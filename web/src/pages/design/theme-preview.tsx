import React from 'react';
import { useModel } from 'umi';
import type { LantingToken } from '@/themes';

const ThemePreviewPage: React.FC = () => {
  const token = useModel('theme') as LantingToken;

  return (
    <>
      <style>{`
        .design-theme *,
        .design-theme *::before,
        .design-theme *::after {
          box-sizing: border-box;
          margin: 0;
          padding: 0;
        }
        .design-theme {
          font-family: ${token.fontFamily};
          background: ${token.colorBgLayout};
          color: ${token.colorText};
          padding: 32px 48px;
          -webkit-font-smoothing: antialiased;
        }
        .design-theme .container {
          max-width: 860px;
          margin: 0 auto;
        }
        .design-theme h1 {
          font-family: 'Georgia', 'Noto Serif SC', serif;
          font-size: 22px;
          font-weight: 500;
          margin-bottom: 6px;
        }
        .design-theme .sub {
          font-size: 12px;
          color: ${token.colorTextDescription};
          margin-bottom: 32px;
        }
        .design-theme .section {
          margin-bottom: 28px;
        }
        .design-theme .section-title {
          font-size: 12px;
          font-weight: 500;
          color: ${token.colorTextSecondary};
          border-bottom: 0.5px solid ${token.colorBorder};
          padding-bottom: 6px;
          margin-bottom: 12px;
        }
        .design-theme .swatch-row {
          display: flex;
          gap: 8px;
          margin-bottom: 12px;
          flex-wrap: wrap;
        }
        .design-theme .swatch {
          border-radius: 8px;
          padding: 14px 16px 12px;
          font-size: 13px;
          font-weight: 500;
          min-width: 90px;
          text-align: center;
        }
        .design-theme .swatch .hex {
          font-size: 11px;
          font-weight: 400;
          margin-top: 4px;
          opacity: 0.75;
        }
        .design-theme .swatch .note {
          font-size: 10px;
          font-weight: 400;
          margin-top: 2px;
          opacity: 0.6;
        }
        .design-theme .swatch.light {
          border: 0.5px solid ${token.colorBorder};
        }
        .design-theme .btn-row {
          display: flex;
          gap: 10px;
          margin-bottom: 14px;
          align-items: center;
        }
        .design-theme .btn {
          border-radius: ${token.borderRadius}px;
          padding: 8px 18px;
          font-size: 13px;
          font-weight: 500;
          cursor: default;
          border: none;
          font-family: inherit;
        }
        .design-theme .btn-primary {
          background: ${token.colorPrimary};
          color: ${token.colorTextLightSolid};
        }
        .design-theme .btn-secondary {
          background: ${token.colorBgContainer};
          color: ${token.colorText};
          border: 0.5px solid ${token.colorBorder};
        }
        .design-theme .btn-ghost {
          background: transparent;
          color: ${token.colorPrimary};
          border: 0.5px solid ${token.colorPrimaryBorder};
        }
        .design-theme .badge {
          display: inline-block;
          padding: 2px 8px;
          border-radius: 4px;
          font-size: 10px;
          font-weight: 500;
        }
        .design-theme .card {
          background: ${token.colorBgContainer};
          border-radius: ${token.borderRadiusLG}px;
          padding: 16px 18px;
          margin-bottom: 10px;
        }
        .design-theme .card-title {
          font-family: 'Georgia', 'Noto Serif SC', serif;
          font-size: 14px;
          font-weight: 500;
          margin-bottom: 4px;
        }
        .design-theme .card-sub {
          font-size: 12px;
          color: ${token.colorTextDescription};
        }
        .design-theme table {
          width: 100%;
          border-collapse: collapse;
          font-size: 12px;
        }
        .design-theme th {
          text-align: left;
          padding: 6px 10px;
          color: ${token.colorTextSecondary};
          font-weight: 500;
          background: ${token.colorBgSubtle};
          border-bottom: 0.5px solid ${token.colorBorder};
        }
        .design-theme td {
          padding: 6px 10px;
          border-bottom: 0.5px solid #F0F0F0;
        }
        .design-theme tr:hover td {
          background: ${token.colorPrimaryBg};
        }
        .design-theme tr.active td {
          background: ${token.colorPrimaryBg};
        }
        .design-theme .mock-layout {
          border: 0.5px solid ${token.colorBorder};
          border-radius: ${token.borderRadiusLG}px;
          overflow: hidden;
        }
        .design-theme .mock-header {
          height: 44px;
          background: ${token.colorBgContainer};
          border-bottom: 0.5px solid ${token.colorBorder};
          display: flex;
          align-items: center;
          padding: 0 16px;
          gap: 10px;
          font-size: 12px;
        }
        .design-theme .mock-header .logo {
          width: 22px;
          height: 22px;
          border-radius: ${token.borderRadius}px;
          background: ${token.colorPrimary};
          color: ${token.colorTextLightSolid};
          display: flex;
          align-items: center;
          justify-content: center;
          font-family: 'Georgia', serif;
          font-size: 11px;
          font-weight: 500;
        }
        .design-theme .mock-header .title {
          font-family: 'Georgia', 'Noto Serif SC', serif;
          font-weight: 500;
        }
        .design-theme .mock-body {
          display: flex;
        }
        .design-theme .mock-sidebar {
          width: 44px;
          background: ${token.colorBgSubtle};
          border-right: 0.5px solid ${token.colorBorder};
          padding: 8px 6px;
          display: flex;
          flex-direction: column;
          gap: 3px;
        }
        .design-theme .mock-nav {
          width: 32px;
          height: 32px;
          border-radius: ${token.borderRadius}px;
          display: flex;
          align-items: center;
          justify-content: center;
          font-size: 14px;
          background: transparent;
          color: ${token.colorTextDescription};
        }
        .design-theme .mock-nav.active {
          background: ${token.colorPrimaryBg};
          color: ${token.colorPrimary};
        }
        .design-theme .mock-content {
          flex: 1;
          padding: 16px;
          min-height: 160px;
        }
        .design-theme .editor-wrap {
          display: flex;
          flex-direction: column;
          height: 300px;
          border: 0.5px solid ${token.colorBorder};
          border-radius: ${token.borderRadiusLG}px;
          overflow: hidden;
        }
        .design-theme .editor-wrap .tabs {
          display: flex;
          align-items: flex-end;
          padding: 0 12px;
          gap: 1px;
          background: ${token.colorBgContainer};
          border-bottom: 0.5px solid ${token.colorBorder};
        }
        .design-theme .editor-wrap .tab {
          padding: 6px 14px;
          border-radius: ${token.borderRadius}px ${token.borderRadius}px 0 0;
          font-size: 11px;
          font-family: ${token.fontFamilyCode};
          color: ${token.colorTextDescription};
          cursor: default;
        }
        .design-theme .editor-wrap .tab.active {
          background: ${token.colorPrimaryBg};
          color: ${token.colorPrimary};
          border: 0.5px solid ${token.colorBorder};
          border-bottom: none;
        }
        .design-theme .editor-wrap .toolbar {
          display: flex;
          align-items: center;
          padding: 0 14px;
          gap: 8px;
          height: 36px;
          border-bottom: 0.5px solid ${token.colorBorder};
        }
        .design-theme .editor-wrap .run-btn {
          display: flex;
          align-items: center;
          gap: 4px;
          padding: 4px 12px;
          border-radius: ${token.borderRadius}px;
          background: ${token.colorPrimary};
          color: ${token.colorTextLightSolid};
          font-size: 11px;
          font-weight: 500;
          cursor: default;
        }
        .design-theme .editor-body-wrap {
          flex: 1;
          display: flex;
          overflow: hidden;
        }
        .design-theme .editor-gutter {
          width: 36px;
          background: ${token.colorBgSubtle};
          border-right: 0.5px solid ${token.colorBorder};
          padding: 10px 8px;
          font-size: 11px;
          color: ${token.colorTextDisabled};
          text-align: right;
          font-family: ${token.fontFamilyCode};
          line-height: 18px;
          user-select: none;
        }
        .design-theme .editor-code-body {
          flex: 1;
          padding: 10px 14px;
          font-size: 12px;
          font-family: ${token.fontFamilyCode};
          line-height: 18px;
          overflow: auto;
        }
        .design-theme .editor-code-body .kw {
          color: ${token.colorPrimary};
          font-weight: 500;
        }
        .design-theme .editor-code-body .str {
          color: ${token.colorSuccess};
        }
        .design-theme .editor-code-body .cm {
          color: ${token.colorTextDescription};
          font-style: italic;
        }
        .design-theme .console {
          height: 100px;
          border-top: 0.5px solid ${token.colorBorder};
          display: flex;
          flex-direction: column;
        }
        .design-theme .console-tabs {
          display: flex;
          align-items: center;
          padding: 0 14px;
          border-bottom: 0.5px solid ${token.colorBorder};
          background: ${token.colorBgContainer};
        }
        .design-theme .console-tab {
          padding: 6px 12px;
          font-size: 11px;
          cursor: default;
          color: ${token.colorTextDescription};
          border-bottom: 2px solid transparent;
          margin-bottom: -1px;
        }
        .design-theme .console-tab.active {
          color: ${token.colorPrimary};
          border-bottom-color: ${token.colorPrimary};
        }
        .design-theme .console-body {
          flex: 1;
          overflow: auto;
          padding: 8px 16px;
        }
        .design-theme .login-wrap {
          display: flex;
          align-items: center;
          justify-content: center;
          height: 320px;
          background: ${token.colorBgLayout};
          border: 0.5px solid ${token.colorBorder};
          border-radius: ${token.borderRadiusLG}px;
        }
        .design-theme .login-card {
          width: 300px;
          background: ${token.colorBgContainer};
          border: 0.5px solid ${token.colorBorder};
          border-radius: ${token.borderRadiusLG}px;
          padding: 36px 28px;
          text-align: center;
        }
        .design-theme .login-logo {
          width: 36px;
          height: 36px;
          border-radius: ${token.borderRadius}px;
          background: ${token.colorPrimary};
          color: ${token.colorTextLightSolid};
          display: flex;
          align-items: center;
          justify-content: center;
          font-family: 'Georgia', serif;
          font-size: 16px;
          font-weight: 500;
          margin: 0 auto 10px;
        }
        .design-theme .login-title {
          font-family: 'Georgia', 'Noto Serif SC', serif;
          font-size: 15px;
          font-weight: 500;
          color: ${token.colorText};
        }
        .design-theme .login-sub {
          font-size: 11px;
          color: ${token.colorTextDescription};
          margin: 4px 0 24px;
        }
        .design-theme .login-input {
          width: 100%;
          height: 38px;
          border-radius: ${token.borderRadius}px;
          border: 0.5px solid ${token.colorBorder};
          background: ${token.colorBgLayout};
          margin-bottom: 10px;
          display: flex;
          align-items: center;
          padding: 0 12px;
          gap: 8px;
          font-size: 13px;
        }
        .design-theme .login-input .icon {
          font-size: 14px;
          color: ${token.colorTextDescription};
        }
        .design-theme .login-btn-mock {
          width: 100%;
          height: 38px;
          border-radius: ${token.borderRadius}px;
          background: ${token.colorPrimary};
          color: ${token.colorTextLightSolid};
          font-size: 13px;
          font-weight: 500;
          border: none;
          cursor: default;
          margin-top: 4px;
        }
      `}</style>

      <div className="design-theme">
        <div className="container">
          <h1>Lanting Stream</h1>
          <div className="sub">主题预览 · 宝蓝 {token.colorPrimary}</div>

          {/* 品牌色 */}
          <div className="section">
            <div className="section-title">品牌色 — Seed Token</div>
            <div className="swatch-row">
              <div className="swatch" style={{ background: token.colorPrimary, color: '#fff' }}>
                主色 Primary<div className="hex">{token.colorPrimary}</div>
              </div>
            </div>
            <div className="section-title" style={{ marginTop: 14 }}>推导品牌配套色</div>
            <div className="swatch-row">
              <div className="swatch" style={{ background: token.colorPrimaryHover, color: '#fff' }}>
                Hover<div className="hex">{token.colorPrimaryHover}</div>
              </div>
              <div className="swatch" style={{ background: '#1C3B6B', color: '#fff' }}>
                Active<div className="hex">#1C3B6B</div>
              </div>
              <div className="swatch light" style={{ background: token.colorPrimaryBg, color: token.colorPrimary, outline: `1.5px solid ${token.colorPrimaryBorder}` }}>
                Bg<div className="hex">{token.colorPrimaryBg}</div>
              </div>
              <div className="swatch" style={{ background: token.colorPrimaryBorder, color: '#fff' }}>
                Border<div className="hex">{token.colorPrimaryBorder}</div>
              </div>
            </div>
            <div className="btn-row" style={{ marginTop: 12 }}>
              <button className="btn btn-primary">主要按钮</button>
              <button className="btn btn-secondary">次要按钮</button>
              <button className="btn btn-ghost">幽灵按钮</button>
            </div>
          </div>

          {/* 语义色 */}
          <div className="section">
            <div className="section-title">语义色</div>
            <div className="swatch-row">
              <div className="swatch" style={{ background: token.colorSuccess, color: '#fff' }}>
                Success<div className="hex">{token.colorSuccess}</div>
              </div>
              <div className="swatch" style={{ background: token.colorWarning, color: '#fff' }}>
                Warning<div className="hex">{token.colorWarning}</div>
              </div>
              <div className="swatch" style={{ background: token.colorError, color: '#fff' }}>
                Error<div className="hex">{token.colorError}</div>
              </div>
              <div className="swatch" style={{ background: token.colorPrimary, color: '#fff' }}>
                Info<div className="hex">{token.colorPrimary}</div>
              </div>
            </div>
            <div style={{ display: 'flex', gap: 8, marginTop: 10 }}>
              <span className="badge" style={{ background: token.colorSuccessBg, color: token.colorSuccess }}>运行中</span>
              <span className="badge" style={{ background: token.colorWarningBg, color: token.colorWarning }}>反压</span>
              <span className="badge" style={{ background: '#FCEBEB', color: token.colorError }}>异常</span>
              <span className="badge" style={{ background: '#EBEBEB', color: token.colorTextDescription }}>已停止</span>
            </div>
          </div>

          {/* 背景 */}
          <div className="section">
            <div className="section-title">背景</div>
            <div className="swatch-row">
              <div className="swatch light" style={{ background: token.colorBgLayout, color: token.colorText, outline: `0.5px solid ${token.colorBorder}` }}>
                colorBgBase<div className="hex">{token.colorBgLayout}</div>
              </div>
              <div className="swatch light" style={{ background: token.colorBgContainer, color: token.colorText, outline: `0.5px solid ${token.colorBorder}` }}>
                colorBgContainer<div className="hex">{token.colorBgContainer}</div>
              </div>
              <div className="swatch light" style={{ background: token.colorBgSubtle, color: token.colorText }}>
                colorFillQuaternary<div className="hex">{token.colorBgSubtle}</div>
              </div>
            </div>
          </div>

          {/* 文字 */}
          <div className="section">
            <div className="section-title">文字</div>
            <div>
              <p style={{ fontSize: 16, fontWeight: 500, fontFamily: 'Georgia, Noto Serif SC, serif', color: token.colorText }}>页面标题 — Georgia Serif 500 {token.colorText}</p>
              <p style={{ fontSize: token.fontSizeBody, color: token.colorText }}>正文内容 — system-ui 14px 400 {token.colorText}</p>
              <p style={{ fontSize: 13, color: token.colorTextSecondary }}>次要信息 — {token.colorTextSecondary}</p>
              <p style={{ fontSize: token.fontSizeCaption, color: token.colorTextDescription }}>辅助说明 — {token.colorTextDescription}</p>
              <p style={{ fontSize: token.fontSizeCaption, color: token.colorTextDisabled }}>禁用文字 — {token.colorTextDisabled}</p>
            </div>
          </div>

          {/* Mock Layout */}
          <div className="section">
            <div className="section-title">Mock Layout</div>
            <div className="mock-layout">
              <div className="mock-header">
                <div className="logo">L</div>
                <span className="title">Lanting Stream</span>
                <span style={{ flex: 1 }} />
                <span style={{ color: token.colorTextDescription }}>admin</span>
              </div>
              <div className="mock-body">
                <div className="mock-sidebar">
                  <div className="mock-nav">⊡</div>
                  <div className="mock-nav active">✦</div>
                  <div className="mock-nav">◈</div>
                  <div className="mock-nav">◎</div>
                  <div className="mock-nav">◉</div>
                </div>
                <div className="mock-content">
                  <div className="card">
                    <div className="card-title">dws_user_funnel</div>
                    <div className="card-sub">Flink SQL · prod-k8s</div>
                  </div>
                  <table>
                    <thead><tr><th>任务名</th><th>集群</th><th>状态</th><th>延迟</th></tr></thead>
                    <tbody>
                      <tr><td>ods_sync</td><td>prod-k8s</td><td><span className="badge" style={{ background: token.colorSuccessBg, color: token.colorSuccess }}>运行中</span></td><td>0ms</td></tr>
                      <tr className="active"><td style={{ color: token.colorPrimary, fontWeight: 500 }}>dwd_clean ▼</td><td>prod-k8s</td><td><span className="badge" style={{ background: token.colorWarningBg, color: token.colorWarning }}>反压</span></td><td>240ms</td></tr>
                      <tr><td>dws_agg</td><td>dev</td><td><span className="badge" style={{ background: token.colorSuccessBg, color: token.colorSuccess }}>运行中</span></td><td>12ms</td></tr>
                      <tr><td>dim_bak</td><td>prod-k8s</td><td><span className="badge" style={{ background: '#EBEBEB', color: token.colorTextDescription }}>已停止</span></td><td>—</td></tr>
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          </div>

          {/* Mock: Editor + Console */}
          <div className="section">
            <div className="section-title">页面 Mock — SQL 编辑器 + 控制台</div>
            <div className="editor-wrap">
              <div className="tabs">
                <div className="tab active">dws_user_funnel.sql</div>
                <div className="tab">ods_user_event.sql</div>
                <div style={{ flex: 1 }} />
                <div className="toolbar" style={{ border: 'none', padding: 0, marginBottom: 6 }}>
                  <span style={{ fontSize: 12, color: token.colorTextDescription, cursor: 'default' }}>💾</span>
                  <div className="run-btn">▶ 运行</div>
                </div>
              </div>
              <div className="editor-body-wrap">
                <div className="editor-gutter">
                  <div>1</div><div>2</div><div>3</div><div>4</div><div>5</div><div>6</div><div>7</div><div>8</div><div>9</div>
                </div>
                <div className="editor-code-body">
                  <div><span className="kw">INSERT INTO</span> dws_user_funnel</div>
                  <div><span className="kw">SELECT</span></div>
                  <div>&nbsp;&nbsp;window_start, window_end, channel,</div>
                  <div>&nbsp;&nbsp;<span className="kw">COUNT</span>(<span className="kw">DISTINCT</span> user_id) <span className="kw">AS</span> uv,</div>
                  <div>&nbsp;&nbsp;<span className="kw">SUM</span>(amount) <span className="kw">AS</span> gmv</div>
                  <div><span className="kw">FROM</span> <span className="kw">TABLE</span>(TUMBLE(...))</div>
                  <div><span className="kw">WHERE</span> event_type <span className="kw">IN</span> (<span className="str">'view'</span>, <span className="str">'click'</span>)</div>
                  <div style={{ color: token.colorTextDescription }}>-- 实时用户行为漏斗</div>
                </div>
              </div>
              <div className="console">
                <div className="console-tabs">
                  <div className="console-tab active">结果</div>
                  <div className="console-tab">日志</div>
                  <div className="console-tab">执行计划</div>
                  <div style={{ flex: 1 }} />
                  <span style={{ fontSize: 11, color: token.colorTextDescription }}>3 行 · 实时更新中</span>
                </div>
                <div className="console-body">
                  <table>
                    <thead><tr><th>window_end</th><th>channel</th><th>uv</th><th>gmv</th></tr></thead>
                    <tbody>
                      <tr><td>10:01:00</td><td>app</td><td>1,234</td><td style={{ color: token.colorPrimary, fontWeight: 500 }}>¥28,450</td></tr>
                      <tr><td>10:01:00</td><td>web</td><td>892</td><td style={{ color: token.colorPrimary, fontWeight: 500 }}>¥15,230</td></tr>
                      <tr><td>10:02:00</td><td>app</td><td>1,108</td><td style={{ color: token.colorPrimary, fontWeight: 500 }}>¥24,860</td></tr>
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          </div>

          {/* Mock: Login */}
          <div className="section">
            <div className="section-title">页面 Mock — 登录</div>
            <div className="login-wrap">
              <div className="login-card">
                <div className="login-logo">L</div>
                <div className="login-title">Lanting Stream</div>
                <div className="login-sub">AI 驱动的 Flink SQL 平台</div>
                <div className="login-input">
                  <span className="icon">👤</span>
                  <span style={{ color: token.colorTextDisabled }}>用户名</span>
                </div>
                <div className="login-input">
                  <span className="icon">🔒</span>
                  <span style={{ color: token.colorTextDisabled }}>密码</span>
                </div>
                <button className="login-btn-mock">登录</button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </>
  );
};

export default ThemePreviewPage;
