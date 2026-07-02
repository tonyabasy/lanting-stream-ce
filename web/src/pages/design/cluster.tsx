import React, { useState } from 'react';
import { useModel } from 'umi';
import type { LantingToken } from '@/themes/parseTheme';

const ClusterPreview: React.FC = () => {
  const token = useModel('theme') as LantingToken;
  const [modalOpen, setModalOpen] = useState(false);

  return (
    <>
      <style>{`
        .design-cluster *,
        .design-cluster *::before,
        .design-cluster *::after {
          box-sizing: border-box;
          margin: 0;
          padding: 0;
        }
        .design-cluster {
          font-family: ${token.fontFamily};
          background: #EBECED;
          color: ${token.colorText};
          display: flex;
          flex-direction: column;
          align-items: center;
          -webkit-font-smoothing: antialiased;
          min-height: 100%;
          padding-bottom: 40px;
        }
        .design-cluster .page-header {
          width: 100%;
          max-width: 960px;
          padding: 28px 24px 12px;
        }
        .design-cluster .page-header h1 {
          font-family: 'Georgia', 'Noto Serif SC', serif;
          font-size: 20px;
          font-weight: 500;
          color: ${token.colorText};
        }
        .design-cluster .page-header .sub {
          font-size: 12px;
          color: ${token.colorTextDescription};
          margin-top: 2px;
        }
        .design-cluster .design-frame {
          width: 960px;
          min-height: 680px;
          background: ${token.colorBgLayout};
          border: 0.5px solid #DADBDD;
          border-radius: ${token.borderRadiusLG}px;
          overflow: hidden;
          display: flex;
          flex-direction: column;
          margin-bottom: 40px;
          box-shadow: 0 2px 12px rgba(0,0,0,0.06);
          position: relative;
        }
        .design-cluster .topbar {
          height: 48px;
          background: ${token.colorBgContainer};
          border-bottom: 0.5px solid ${token.colorBorder};
          display: flex;
          align-items: center;
          padding: 0 18px;
          gap: 10px;
          flex-shrink: 0;
        }
        .design-cluster .logo-icon {
          width: 26px;
          height: 26px;
          border-radius: ${token.borderRadius}px;
          background: ${token.colorPrimary};
          color: ${token.colorTextLightSolid};
          display: flex;
          align-items: center;
          justify-content: center;
          font-family: 'Georgia', 'Noto Serif SC', serif;
          font-size: 12px;
          font-weight: 500;
        }
        .design-cluster .logo-text {
          font-family: 'Georgia', 'Noto Serif SC', serif;
          font-size: 14px;
          font-weight: 500;
          color: ${token.colorText};
        }
        .design-cluster .no-group {
          font-size: 11px;
          color: ${token.colorTextDescription};
          margin-left: 8px;
        }
        .design-cluster .spacer {
          flex: 1;
        }
        .design-cluster .body-wrap {
          flex: 1;
          display: flex;
          overflow: hidden;
        }
        .design-cluster .sidebar {
          width: 180px;
          background: ${token.colorBgContainer};
          border-right: 0.5px solid #EEEEEE;
          display: flex;
          flex-direction: column;
          flex-shrink: 0;
        }
        .design-cluster .nav-list {
          flex: 1;
          padding: 12px 0;
        }
        .design-cluster .nav-item {
          display: flex;
          align-items: center;
          gap: 10px;
          padding: 8px 18px;
          font-size: 13px;
          color: ${token.colorTextSecondary};
          cursor: default;
          transition: background 0.15s, color 0.15s;
        }
        .design-cluster .nav-item:hover {
          background: ${token.colorPrimaryBg};
        }
        .design-cluster .nav-item.active {
          background: ${token.colorPrimaryBg};
          color: ${token.colorPrimary};
          font-weight: 500;
        }
        .design-cluster .nav-item .icon {
          font-size: 16px;
          width: 20px;
          text-align: center;
        }
        .design-cluster .nav-item .arrow {
          margin-left: auto;
          font-size: 11px;
          color: ${token.colorTextDisabled};
        }
        .design-cluster .user-info {
          padding: 14px 18px;
          border-top: 0.5px solid #EEEEEE;
          display: flex;
          align-items: center;
          gap: 8px;
          font-size: 12px;
          color: ${token.colorTextSecondary};
        }
        .design-cluster .user-avatar {
          width: 24px;
          height: 24px;
          border-radius: 50%;
          background: ${token.colorBgSubtle};
          display: flex;
          align-items: center;
          justify-content: center;
          font-size: 12px;
          color: ${token.colorTextDescription};
        }
        .design-cluster .content {
          flex: 1;
          padding: 20px 22px;
          overflow: hidden;
          display: flex;
          flex-direction: column;
        }
        .design-cluster .content-header {
          display: flex;
          align-items: center;
          margin-bottom: 18px;
        }
        .design-cluster .content-header h2 {
          font-family: 'Georgia', 'Noto Serif SC', serif;
          font-size: 16px;
          font-weight: 500;
          color: ${token.colorText};
        }
        .design-cluster .btn {
          height: 32px;
          padding: 0 14px;
          border-radius: ${token.borderRadius}px;
          font-size: 12px;
          font-weight: 500;
          cursor: pointer;
          display: flex;
          align-items: center;
          gap: 4px;
          border: none;
          font-family: inherit;
          transition: background 0.15s, color 0.15s;
        }
        .design-cluster .btn-primary {
          background: ${token.colorPrimary};
          color: ${token.colorTextLightSolid};
        }
        .design-cluster .btn-primary:hover {
          background: ${token.colorPrimaryHover};
        }
        .design-cluster .btn-secondary {
          background: ${token.colorBgContainer};
          color: ${token.colorTextSecondary};
          border: 0.5px solid ${token.colorBorder};
        }
        .design-cluster .btn-secondary:hover {
          background: ${token.colorPrimaryBg};
          color: ${token.colorPrimary};
        }
        .design-cluster .btn-ghost {
          background: transparent;
          color: ${token.colorPrimary};
          border: 0.5px solid ${token.colorPrimaryBorder};
        }
        .design-cluster .btn-ghost:hover {
          background: ${token.colorPrimaryBg};
        }
        .design-cluster .btn-danger {
          background: transparent;
          color: ${token.colorError};
          border: 0.5px solid #E0C0C0;
        }
        .design-cluster .btn-danger:hover {
          background: #FCEBEB;
        }
        .design-cluster .btn-disabled {
          background: ${token.colorBgSubtle};
          color: ${token.colorTextDisabled};
          cursor: not-allowed;
        }
        .design-cluster .card-grid {
          display: grid;
          grid-template-columns: repeat(2, 1fr);
          gap: 16px;
          overflow-y: auto;
          flex: 1;
          padding: 2px;
        }
        .design-cluster .cluster-card {
          background: ${token.colorBgContainer};
          border: 0.5px solid ${token.colorBorder};
          border-radius: ${token.borderRadius}px;
          padding: 18px 20px 0;
          position: relative;
          box-shadow: ${token.boxShadow};
          transition: border-color 0.15s;
          display: flex;
          flex-direction: column;
        }
        .design-cluster .cluster-card:hover {
          border-color: #CCC;
        }
        .design-cluster .cluster-card.disabled {
          opacity: 0.45;
        }
        .design-cluster .card-head {
          display: flex;
          align-items: center;
          margin-bottom: 8px;
        }
        .design-cluster .card-title {
          font-size: 15px;
          font-weight: 600;
          color: ${token.colorText};
        }
        .design-cluster .card-logo {
          width: 36px;
          height: 36px;
          border-radius: ${token.borderRadius}px;
          display: flex;
          align-items: center;
          justify-content: center;
          font-size: 11px;
          font-weight: 700;
          flex-shrink: 0;
          margin-right: 12px;
          color: #fff;
          letter-spacing: -0.5px;
        }
        .design-cluster .card-logo.yarn-session { background: linear-gradient(135deg, #E17E30, #D96620); }
        .design-cluster .card-logo.yarn-app { background: linear-gradient(135deg, #E17E30, #B84D10); }
        .design-cluster .card-logo.k8s-session { background: linear-gradient(135deg, #326CE5, #1D4FB8); }
        .design-cluster .card-logo.k8s-app { background: linear-gradient(135deg, #326CE5, #0E3396); }
        .design-cluster .card-logo.local { background: linear-gradient(135deg, #7B8794, #5A6672); }
        .design-cluster .card-info {
          flex: 1;
          min-width: 0;
        }
        .design-cluster .card-meta {
          font-size: 12px;
          color: ${token.colorTextDescription};
          margin-bottom: 2px;
        }
        .design-cluster .card-version {
          font-size: 12px;
          color: ${token.colorTextDescription};
          margin-bottom: 2px;
          padding-left: 48px;
        }
        .design-cluster .card-resource {
          font-size: 12px;
          color: ${token.colorTextSecondary};
          margin-bottom: 12px;
          padding-left: 48px;
          display: flex;
          align-items: center;
          gap: 12px;
        }
        .design-cluster .card-resource .r-label {
          font-size: 10px;
          color: ${token.colorTextDescription};
          text-transform: uppercase;
          letter-spacing: 0.5px;
        }
        .design-cluster .card-resource .r-value {
          font-family: 'ui-monospace', 'SF Mono', monospace;
          font-weight: 500;
        }
        .design-cluster .card-resource .r-sep {
          color: ${token.colorBorder};
        }
        .design-cluster .card-status {
          display: flex;
          align-items: center;
          gap: 6px;
          font-size: 12px;
          margin-bottom: 14px;
          padding-left: 48px;
        }
        .design-cluster .card-status .dot {
          width: 7px;
          height: 7px;
          border-radius: 50%;
          flex-shrink: 0;
        }
        .design-cluster .card-status .dot.success { background: ${token.colorSuccess}; }
        .design-cluster .card-status .dot.error { background: ${token.colorError}; }
        .design-cluster .card-status .dot.unknown { background: #CCC; }
        .design-cluster .card-status .text.success { color: ${token.colorSuccess}; }
        .design-cluster .card-status .text.error { color: ${token.colorError}; }
        .design-cluster .card-status .text.unknown { color: ${token.colorTextDescription}; }
        .design-cluster .card-actions {
          display: flex;
          align-items: stretch;
          margin: 12px -20px 0;
          border-top: 0.5px solid ${token.colorBorder};
          margin-top: auto;
        }
        .design-cluster .card-actions .btn-icon {
          flex: 1;
          height: 40px;
          border-radius: 0;
          border: none;
          border-right: 0.5px solid ${token.colorBorder};
          background: transparent;
          font-size: 16px;
          display: flex;
          align-items: center;
          justify-content: center;
          color: ${token.colorTextSecondary};
          cursor: pointer;
          transition: background 0.15s, color 0.15s;
        }
        .design-cluster .card-actions .btn-icon:last-child { border-right: none; }
        .design-cluster .card-actions .btn-icon:hover { background: ${token.colorPrimaryBg}; color: ${token.colorPrimary}; }
        .design-cluster .card-actions .btn-icon.danger:hover { background: #FCEBEB; color: ${token.colorError}; }
        .design-cluster .card-actions .btn-icon.dis {
          color: ${token.colorTextDisabled};
          cursor: not-allowed;
        }
        .design-cluster .card-actions .btn-icon.dis:hover {
          background: transparent;
          color: ${token.colorTextDisabled};
        }
        .design-cluster .toggle-wrap {
          display: flex;
          align-items: center;
          flex-shrink: 0;
          margin-left: 8px;
        }
        .design-cluster .toggle {
          width: 34px;
          height: 20px;
          border-radius: 10px;
          background: ${token.colorSuccess};
          border: none;
          cursor: pointer;
          position: relative;
        }
        .design-cluster .toggle::after {
          content: '';
          position: absolute;
          top: 2px;
          left: 16px;
          width: 16px;
          height: 16px;
          border-radius: 50%;
          background: #fff;
          box-shadow: 0 1px 2px rgba(0,0,0,0.15);
        }
        .design-cluster .toggle.off { background: #CCC; }
        .design-cluster .toggle.off::after { left: 2px; }
        .design-cluster .modal-overlay {
          position: absolute;
          inset: 0;
          background: rgba(0,0,0,0.3);
          display: flex;
          align-items: center;
          justify-content: center;
          z-index: 20;
        }
        .design-cluster .modal {
          width: 380px;
          background: ${token.colorBgContainer};
          border-radius: ${token.borderRadiusLG}px;
          box-shadow: 0 8px 30px rgba(0,0,0,0.12);
          overflow: hidden;
        }
        .design-cluster .modal-header {
          padding: 14px 20px 12px;
          font-size: 15px;
          font-weight: 600;
          color: ${token.colorText};
          border-bottom: 0.5px solid #EEEEEE;
        }
        .design-cluster .modal-body {
          padding: 14px 20px;
        }
        .design-cluster .modal-footer {
          padding: 12px 20px;
          display: flex;
          justify-content: flex-end;
          gap: 8px;
          border-top: 0.5px solid #EEEEEE;
        }
        .design-cluster .form-row {
          margin-bottom: 10px;
        }
        .design-cluster .form-label {
          font-size: 12px;
          font-weight: 500;
          color: ${token.colorTextSecondary};
          margin-bottom: 6px;
          display: block;
        }
        .design-cluster .form-input,
        .design-cluster .form-select {
          width: 100%;
          height: 32px;
          border: 0.5px solid ${token.colorBorder};
          border-radius: ${token.borderRadius}px;
          padding: 0 12px;
          font-family: inherit;
          font-size: 13px;
          color: ${token.colorText};
          background: ${token.colorBgLayout};
          outline: none;
        }
        .design-cluster .form-input:focus {
          border-color: ${token.colorPrimary};
          box-shadow: 0 0 0 3px ${token.colorPrimaryBg};
        }
        .design-cluster .form-input::placeholder {
          color: ${token.colorTextDisabled};
        }
        .design-cluster .form-hint {
          font-size: 11px;
          color: ${token.colorTextDescription};
          margin-top: 4px;
        }
        .design-cluster .form-input-with-btn {
          display: flex;
          gap: 8px;
        }
        .design-cluster .form-input-with-btn .form-input {
          flex: 1;
        }
        .design-cluster .palette {
          width: 960px;
          margin-bottom: 40px;
        }
        .design-cluster .palette h3 {
          font-family: 'Georgia', 'Noto Serif SC', serif;
          font-size: 14px;
          font-weight: 500;
          margin-bottom: 10px;
          color: ${token.colorText};
        }
        .design-cluster .palette-row {
          display: flex;
          gap: 8px;
          flex-wrap: wrap;
        }
        .design-cluster .palette-item {
          display: flex;
          align-items: center;
          gap: 8px;
          padding: 6px 12px;
          border-radius: ${token.borderRadiusSM}px;
          border: 0.5px solid ${token.colorBorder};
          background: #fff;
          font-size: 11px;
          color: ${token.colorTextSecondary};
        }
        .design-cluster .palette-dot {
          width: 14px;
          height: 14px;
          border-radius: 3px;
        }
      `}</style>

      <div className="design-cluster">
        <div className="page-header">
          <h1>集群管理 页面设计稿</h1>
          <div className="sub">Lanting Stream · 集群配置管理 · 宝蓝主题 {token.colorPrimary}</div>
        </div>

        <div className="design-frame">
          <div className="topbar">
            <div className="logo-icon">L</div>
            <span className="logo-text">Lanting</span>
            <span className="no-group">暂无用户组</span>
            <span className="spacer" />
          </div>

          <div className="body-wrap">
            <div className="sidebar">
              <div className="nav-list">
                <div className="nav-item"><span className="icon">🏠</span>首页</div>
                <div className="nav-item"><span className="icon">🛠</span>研发<span className="arrow">›</span></div>
                <div className="nav-item"><span className="icon">🚀</span>发布<span className="arrow">›</span></div>
                <div className="nav-item"><span className="icon">⚙</span>运维<span className="arrow">›</span></div>
                <div className="nav-item active"><span className="icon">☁</span>集群<span className="arrow">‹</span></div>
                <div className="nav-item"><span className="icon">🔑</span>权限<span className="arrow">›</span></div>
              </div>
              <div className="user-info">
                <div className="user-avatar">👤</div>
                <span>super_admin</span>
              </div>
            </div>

            <div className="content">
              <div className="content-header">
                <h2>集群管理</h2>
                <span className="spacer" />
                <button className="btn btn-primary" onClick={() => setModalOpen(true)}>+ 新建集群</button>
              </div>

              <div className="card-grid">
                <div className="cluster-card">
                  <div className="card-head">
                    <div className="card-logo yarn-session">YARN<br />S</div>
                    <div className="card-info">
                      <div className="card-title">prod-yarn</div>
                      <div className="card-meta" style={{ paddingLeft: 0 }}>YARN · Session</div>
                    </div>
                    <div className="toggle-wrap"><button className="toggle" /></div>
                  </div>
                  <div className="card-version">Flink 2.2.0</div>
                  <div className="card-resource">
                    <span className="r-label">CPU</span>
                    <span className="r-value">16/32</span>
                    <span className="r-label">Core</span>
                    <span className="r-sep">|</span>
                    <span className="r-label">MEM</span>
                    <span className="r-value">24/64</span>
                    <span className="r-label">GB</span>
                  </div>
                  <div className="card-status">
                    <span className="dot success" />
                    <span className="text success">连接成功</span>
                  </div>
                  <div className="card-actions">
                    <button className="btn-icon">🔌</button>
                    <button className="btn-icon">✏️</button>
                    <button className="btn-icon danger">🗑</button>
                  </div>
                </div>

                <div className="cluster-card">
                  <div className="card-head">
                    <div className="card-logo local">LOC</div>
                    <div className="card-info">
                      <div className="card-title">dev-local</div>
                      <div className="card-meta" style={{ paddingLeft: 0 }}>Local · Mini-cluster</div>
                    </div>
                    <div className="toggle-wrap"><button className="toggle off" /></div>
                  </div>
                  <div className="card-version">Flink 2.2.0</div>
                  <div className="card-status">
                    <span className="dot unknown" />
                    <span className="text unknown">未知</span>
                  </div>
                  <div className="card-actions">
                    <button className="btn-icon">🔌</button>
                    <button className="btn-icon">✏️</button>
                    <button className="btn-icon danger">🗑</button>
                  </div>
                </div>

                <div className="cluster-card disabled">
                  <div className="card-head">
                    <div className="card-logo k8s-app">K8S<br />A</div>
                    <div className="card-info">
                      <div className="card-title">test-k8s</div>
                      <div className="card-meta" style={{ paddingLeft: 0 }}>Kubernetes · Application</div>
                    </div>
                    <div className="toggle-wrap"><button className="toggle off" /></div>
                  </div>
                  <div className="card-version">Flink 2.2.0</div>
                  <div className="card-status">
                    <span className="dot error" />
                    <span className="text error">连接失败</span>
                  </div>
                  <div className="card-actions">
                    <button className="btn-icon dis">🔌</button>
                    <button className="btn-icon dis">✏️</button>
                    <button className="btn-icon dis">🗑</button>
                  </div>
                </div>

                <div className="cluster-card">
                  <div className="card-head">
                    <div className="card-logo yarn-app">YARN<br />A</div>
                    <div className="card-info">
                      <div className="card-title">staging-flink</div>
                      <div className="card-meta" style={{ paddingLeft: 0 }}>YARN · Application</div>
                    </div>
                    <div className="toggle-wrap"><button className="toggle" /></div>
                  </div>
                  <div className="card-version">Flink 2.2.0</div>
                  <div className="card-status">
                    <span className="dot success" />
                    <span className="text success">连接成功</span>
                  </div>
                  <div className="card-actions">
                    <button className="btn-icon">🔌</button>
                    <button className="btn-icon">✏️</button>
                    <button className="btn-icon danger">🗑</button>
                  </div>
                </div>
              </div>
            </div>
          </div>

          {modalOpen && (
            <div className="modal-overlay" onClick={() => setModalOpen(false)}>
              <div className="modal" onClick={(e) => e.stopPropagation()}>
                <div className="modal-header">新建集群</div>
                <div className="modal-body">
                  <div className="form-row">
                    <label className="form-label">集群名称</label>
                    <input className="form-input" type="text" placeholder="请输入集群名称" readOnly />
                  </div>
                  <div className="form-row">
                    <label className="form-label">FLINK_HOME</label>
                    <div className="form-input-with-btn">
                      <input className="form-input" type="text" placeholder="/opt/flink" readOnly />
                      <button className="btn btn-secondary" style={{ height: 32, flexShrink: 0, fontSize: 11 }}>检测版本</button>
                    </div>
                    <div className="form-hint" style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                      <span style={{ color: token.colorSuccess, fontSize: 12 }}>✓</span>
                      <span>检测到 Flink 2.2.0</span>
                    </div>
                  </div>
                  <div className="form-row">
                    <label className="form-label">资源类型 / 部署模式</label>
                    <div style={{ display: 'flex', gap: 8 }}>
                      <select className="form-select" style={{ flex: 1 }}><option>YARN</option><option>Kubernetes</option><option>Local</option></select>
                      <select className="form-select" style={{ flex: 1 }}><option>Session</option><option>Application</option></select>
                    </div>
                  </div>
                  <div style={{ border: `0.5px solid #EEEEEE`, borderRadius: token.borderRadius, padding: '10px 12px', background: token.colorBgLayout }}>
                    <div className="form-label" style={{ fontWeight: 600, color: token.colorText, marginBottom: 8 }}>YARN Session 参数</div>
                    <div className="form-row">
                      <label className="form-label">YARN Application ID</label>
                      <input className="form-input" type="text" placeholder="application_xxx" readOnly />
                    </div>
                    <div style={{ display: 'flex', gap: 10 }}>
                      <div style={{ flex: 1 }}>
                        <label className="form-label">JM 内存</label>
                        <input className="form-input" type="text" placeholder="1024 MB" readOnly />
                      </div>
                      <div style={{ flex: 1 }}>
                        <label className="form-label">TM 内存</label>
                        <input className="form-input" type="text" placeholder="2048 MB" readOnly />
                      </div>
                    </div>
                  </div>
                </div>
                <div className="modal-footer">
                  <button className="btn btn-secondary" onClick={() => setModalOpen(false)}>取消</button>
                  <button className="btn btn-primary" onClick={() => setModalOpen(false)}>确认</button>
                </div>
              </div>
            </div>
          )}
        </div>

        <div className="palette">
          <h3>配色参考</h3>
          <div className="palette-row">
            <div className="palette-item">
              <div className="palette-dot" style={{ background: token.colorPrimary }} />
              <span>Primary {token.colorPrimary}</span>
            </div>
            <div className="palette-item">
              <div className="palette-dot" style={{ background: token.colorSuccess }} />
              <span>Success {token.colorSuccess}</span>
            </div>
            <div className="palette-item">
              <div className="palette-dot" style={{ background: token.colorError }} />
              <span>Error {token.colorError}</span>
            </div>
            <div className="palette-item">
              <div className="palette-dot" style={{ background: token.colorBgLayout, border: `0.5px solid ${token.colorBorder}` }} />
              <span>Bg Layout</span>
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

export default ClusterPreview;
