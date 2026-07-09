import React from 'react';
import { useModel } from 'umi';
import type { LantingToken } from '@/themes';

const EditorPreview: React.FC = () => {
  const token = useModel('theme') as LantingToken;

  return (
    <>
      <style>{`
        .design-editor *,
        .design-editor *::before,
        .design-editor *::after {
          box-sizing: border-box;
          margin: 0;
          padding: 0;
        }
        .design-editor {
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
        .design-editor .page-header {
          width: 100%;
          max-width: 1300px;
          padding: 28px 24px 12px;
        }
        .design-editor .page-header h1 {
          font-family: 'Georgia', 'Noto Serif SC', serif;
          font-size: 20px;
          font-weight: 500;
          color: ${token.colorText};
        }
        .design-editor .page-header .sub {
          font-size: 12px;
          color: ${token.colorTextDescription};
          margin-top: 2px;
        }
        .design-editor .design-frame {
          width: 1300px;
          height: 800px;
          background: ${token.colorBgLayout};
          border: 0.5px solid #DADBDD;
          border-radius: ${token.borderRadiusLG}px;
          overflow: hidden;
          display: flex;
          flex-direction: column;
          margin-bottom: 40px;
          box-shadow: 0 2px 12px rgba(0,0,0,0.06);
        }
        .design-editor .topbar {
          height: 40px;
          background: ${token.colorBgContainer};
          border-bottom: 0.5px solid ${token.colorBorder};
          display: flex;
          align-items: center;
          padding: 0 12px;
          gap: 8px;
          flex-shrink: 0;
        }
        .design-editor .topbar .logo {
          width: 22px;
          height: 22px;
          border-radius: ${token.borderRadius}px;
          background: ${token.colorPrimary};
          color: ${token.colorTextLightSolid};
          display: flex;
          align-items: center;
          justify-content: center;
          font-family: 'Georgia', 'Noto Serif SC', serif;
          font-size: 11px;
          font-weight: 500;
          flex-shrink: 0;
          margin-right: 4px;
        }
        .design-editor .spacer { flex: 1; }
        .design-editor .topbar-btn {
          height: 28px;
          padding: 0 12px;
          border-radius: ${token.borderRadius}px;
          font-size: 12px;
          font-weight: 500;
          cursor: default;
          display: flex;
          align-items: center;
          gap: 4px;
          border: none;
          font-family: inherit;
          white-space: nowrap;
        }
        .design-editor .topbar-btn.primary {
          background: ${token.colorPrimary};
          color: ${token.colorTextLightSolid};
        }
        .design-editor .topbar-btn.icon {
          width: 28px;
          padding: 0;
          color: ${token.colorTextSecondary};
          background: transparent;
          justify-content: center;
          font-size: 14px;
        }
        .design-editor .topbar-btn.icon:hover {
          background: ${token.colorPrimaryBg};
          color: ${token.colorPrimary};
        }
        .design-editor .topbar-divider {
          width: 0.5px;
          height: 16px;
          background: ${token.colorBorder};
          margin: 0 4px;
        }
        .design-editor .run-toggle {
          display: flex;
          align-items: center;
          justify-content: center;
          width: 28px;
          height: 28px;
          padding: 0;
          border-radius: ${token.borderRadius}px;
          font-size: 14px;
          cursor: default;
          border: none;
        }
        .design-editor .run-toggle.running {
          background: ${token.colorError};
          color: ${token.colorTextLightSolid};
        }
        .design-editor .main {
          flex: 1;
          display: flex;
          overflow: hidden;
        }
        .design-editor .left-toolbar {
          width: 40px;
          background: ${token.colorBgSubtle};
          border-right: 0.5px solid ${token.colorBorder};
          display: flex;
          flex-direction: column;
          align-items: center;
          padding: 8px 4px;
          gap: 2px;
          flex-shrink: 0;
        }
        .design-editor .tool-btn {
          width: 32px;
          height: 32px;
          border-radius: ${token.borderRadius}px;
          display: flex;
          align-items: center;
          justify-content: center;
          font-size: 13px;
          cursor: default;
          color: ${token.colorTextDescription};
          background: transparent;
          border: none;
        }
        .design-editor .tool-btn:hover { background: #2A5CA008; color: ${token.colorTextSecondary}; }
        .design-editor .tool-btn.active {
          background: ${token.colorPrimaryBg};
          color: ${token.colorPrimary};
        }
        .design-editor .side-panel {
          width: 200px;
          border-right: 0.5px solid #EEEEEE;
          display: flex;
          flex-direction: column;
          flex-shrink: 0;
          background: ${token.colorBgContainer};
        }
        .design-editor .side-panel .panel-tabs {
          display: flex;
          border-bottom: 0.5px solid #EEEEEE;
          flex-shrink: 0;
        }
        .design-editor .panel-tab {
          flex: 1;
          padding: 7px 0;
          text-align: center;
          font-size: 11px;
          font-weight: 500;
          cursor: default;
          color: ${token.colorTextDescription};
          border-bottom: 2px solid transparent;
          margin-bottom: -0.5px;
        }
        .design-editor .panel-tab.active {
          color: ${token.colorPrimary};
          border-bottom-color: ${token.colorPrimary};
        }
        .design-editor .side-panel .panel-body {
          flex: 1;
          overflow: hidden;
          padding: 6px 0;
        }
        .design-editor .file-tree { font-size: 12px; }
        .design-editor .file-tree .file {
          display: flex;
          align-items: center;
          gap: 6px;
          padding: 5px 14px;
          cursor: default;
          color: ${token.colorTextSecondary};
        }
        .design-editor .file-tree .file:hover { background: ${token.colorPrimaryBg}; }
        .design-editor .file-tree .file.active {
          background: ${token.colorPrimaryBg};
          color: ${token.colorPrimary};
          font-weight: 500;
        }
        .design-editor .file-tree .folder {
          display: flex;
          align-items: center;
          gap: 6px;
          padding: 5px 14px;
          cursor: default;
          color: ${token.colorTextSecondary};
        }
        .design-editor .file-tree .children {
          padding-left: 20px;
        }
        .design-editor .editor-area {
          flex: 1;
          display: flex;
          flex-direction: column;
          min-width: 0;
        }
        .design-editor .editor-tabs {
          display: flex;
          align-items: flex-end;
          background: ${token.colorBgContainer};
          border-bottom: 0.5px solid #EEEEEE;
          padding: 0 8px;
          gap: 0;
          flex-shrink: 0;
          overflow: hidden;
        }
        .design-editor .editor-tab {
          padding: 6px 14px;
          font-size: 11px;
          cursor: default;
          color: ${token.colorTextDescription};
          font-family: ${token.fontFamilyCode};
          border-right: 0.5px solid #EEEEEE;
          display: flex;
          align-items: center;
          gap: 6px;
          white-space: nowrap;
        }
        .design-editor .editor-tab.active {
          background: ${token.colorBgLayout};
          color: ${token.colorPrimary};
          border-bottom: 2px solid ${token.colorPrimary};
          margin-bottom: -0.5px;
        }
        .design-editor .editor-tab .dot {
          width: 6px;
          height: 6px;
          border-radius: 50%;
          background: ${token.colorPrimary};
        }
        .design-editor .editor-body {
          flex: 1;
          display: flex;
          overflow: hidden;
          position: relative;
        }
        .design-editor .editor-gutter {
          width: 38px;
          background: #F4F5F7;
          border-right: 0.5px solid #EEEEEE;
          padding: 10px 6px;
          text-align: right;
          flex-shrink: 0;
          font-family: ${token.fontFamilyCode};
          font-size: 11px;
          line-height: 20px;
          color: ${token.colorTextDisabled};
          user-select: none;
          overflow: hidden;
        }
        .design-editor .editor-code {
          flex: 1;
          padding: 10px 16px;
          overflow: hidden;
          font-family: ${token.fontFamilyCode};
          font-size: 13px;
          line-height: 20px;
          background: ${token.colorBgLayout};
        }
        .design-editor .editor-code .kw { color: ${token.colorPrimary}; font-weight: 500; }
        .design-editor .editor-code .str { color: ${token.colorSuccess}; }
        .design-editor .editor-code .fn { color: #7B3FA3; }
        .design-editor .editor-code .cm { color: ${token.colorTextDescription}; font-style: italic; }
        .design-editor .editor-code .cursor-line {
          background: ${token.colorPrimaryBg};
          border-left: 2px solid ${token.colorPrimary};
        }
        .design-editor .right-toolbar {
          width: 36px;
          background: ${token.colorBgSubtle};
          border-left: 0.5px solid ${token.colorBorder};
          display: flex;
          flex-direction: column;
          align-items: center;
          padding: 8px 4px;
          gap: 4px;
          flex-shrink: 0;
        }
        .design-editor .bottom-panel {
          height: 180px;
          border-top: 0.5px solid ${token.colorBorder};
          display: flex;
          flex-direction: column;
          background: ${token.colorBgContainer};
          flex-shrink: 0;
        }
        .design-editor .bottom-panel .panel-tabs {
          display: flex;
          align-items: center;
          border-bottom: 0.5px solid #EEEEEE;
          background: ${token.colorBgSubtle};
          flex-shrink: 0;
        }
        .design-editor .bottom-panel .panel-body {
          flex: 1;
          overflow: hidden;
          padding: 8px 16px;
          font-size: 12px;
        }
        .design-editor .run-table {
          width: 100%;
          font-size: 11px;
          border-collapse: collapse;
        }
        .design-editor .run-table th {
          text-align: left;
          padding: 5px 10px;
          color: ${token.colorTextDescription};
          font-weight: 500;
          font-size: 10px;
          border-bottom: 0.5px solid #EEEEEE;
        }
        .design-editor .run-table td {
          padding: 5px 10px;
          border-bottom: 0.5px solid #F4F5F7;
        }
        .design-editor .status-badge {
          display: inline-block;
          padding: 1px 8px;
          border-radius: 10px;
          font-size: 10px;
          font-weight: 500;
        }
        .design-editor .status-success { background: ${token.colorSuccessBg}; color: ${token.colorSuccess}; }
        .design-editor .status-running { background: #E8F0FE; color: ${token.colorPrimary}; }
        .design-editor .status-failed { background: #FCEBEB; color: ${token.colorError}; }
        .design-editor .status-cancelled { background: #F5F5F5; color: ${token.colorTextDescription}; }
        .design-editor .statusbar {
          height: 24px;
          background: ${token.colorBgContainer};
          border-top: 0.5px solid ${token.colorBorder};
          display: flex;
          align-items: center;
          padding: 0 12px;
          gap: 12px;
          flex-shrink: 0;
        }
        .design-editor .statusbar .breadcrumb {
          font-size: 11px;
          color: ${token.colorTextDescription};
          display: flex;
          align-items: center;
          gap: 4px;
        }
        .design-editor .legend {
          width: 100%;
          max-width: 1300px;
          display: flex;
          gap: 20px;
          margin-bottom: 28px;
          font-size: 12px;
          color: ${token.colorTextSecondary};
        }
        .design-editor .legend .block {
          background: ${token.colorBgContainer};
          border: 0.5px solid #DADBDD;
          border-radius: ${token.borderRadiusLG}px;
          padding: 14px 18px;
          flex: 1;
        }
        .design-editor .legend .block h3 {
          font-family: 'Georgia', 'Noto Serif SC', serif;
          font-size: 13px;
          font-weight: 500;
          margin-bottom: 8px;
          color: ${token.colorText};
        }
        .design-editor .legend .block ul { list-style: none; }
        .design-editor .legend .block li { line-height: 22px; }
        .design-editor .swatch-inline {
          display: inline-block;
          width: 12px;
          height: 12px;
          border-radius: 3px;
          vertical-align: middle;
          margin-right: 6px;
        }
      `}</style>

      <div className="design-editor">
        <div className="page-header">
          <h1>Editor 页面设计稿</h1>
          <div className="sub">Lanting Stream · 全屏沉浸式 Flink SQL 编辑器 · 宝蓝主题 {token.colorPrimary}</div>
        </div>

        <div className="legend">
          <div className="block">
            <h3>配色方案</h3>
            <ul>
              <li><span className="swatch-inline" style={{ background: token.colorPrimary }} />主色 Primary {token.colorPrimary}</li>
              <li><span className="swatch-inline" style={{ background: token.colorPrimaryHover }} />悬停 Hover {token.colorPrimaryHover}</li>
              <li><span className="swatch-inline" style={{ background: '#1C3B6B' }} />激活 Active #1C3B6B</li>
              <li><span className="swatch-inline" style={{ background: token.colorPrimaryBorder, border: `0.5px solid ${token.colorBorder}` }} />边框 Primary-border</li>
              <li><span className="swatch-inline" style={{ background: token.colorPrimaryBg, border: `0.5px solid ${token.colorBorder}` }} />背景 Primary-bg</li>
            </ul>
          </div>
          <div className="block">
            <h3>背景层级</h3>
            <ul>
              <li><span className="swatch-inline" style={{ background: token.colorBgLayout, border: `0.5px solid ${token.colorBorder}` }} />Layout 底板 {token.colorBgLayout}</li>
              <li><span className="swatch-inline" style={{ background: token.colorBgContainer, border: `0.5px solid ${token.colorBorder}` }} />Container 容器 {token.colorBgContainer}</li>
              <li><span className="swatch-inline" style={{ background: token.colorBgSubtle }} />Subtle 工具栏</li>
              <li><span className="swatch-inline" style={{ background: '#FAFBFC', border: `0.5px solid ${token.colorBorder}` }} />Editor 编辑器</li>
            </ul>
          </div>
          <div className="block">
            <h3>语义色</h3>
            <ul>
              <li><span className="swatch-inline" style={{ background: token.colorSuccess }} />Success {token.colorSuccess}</li>
              <li><span className="swatch-inline" style={{ background: token.colorWarning }} />Warning {token.colorWarning}</li>
              <li><span className="swatch-inline" style={{ background: token.colorError }} />Error {token.colorError}</li>
            </ul>
          </div>
          <div className="block">
            <h3>文字</h3>
            <ul>
              <li style={{ color: token.colorText }}><b>正文</b> {token.colorText}</li>
              <li style={{ color: token.colorTextSecondary }}>次要 {token.colorTextSecondary}</li>
              <li style={{ color: token.colorTextDescription }}>辅助 {token.colorTextDescription}</li>
              <li style={{ color: token.colorTextDisabled }}>禁用 {token.colorTextDisabled}</li>
            </ul>
          </div>
        </div>

        <div className="design-frame">
          <div className="topbar" style={{ gap: 0 }}>
            <div className="logo">L</div>
            <span className="spacer" />
            <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
              <button className="run-toggle running">⏹</button>
              <button className="topbar-btn icon">✓</button>
              <span className="topbar-divider" />
              <button className="topbar-btn icon">{'{ }'}</button>
              <span style={{ color: token.colorTextDisabled, fontSize: 16, margin: '0 2px' }}>｜</span>
              <button className="topbar-btn icon">⚙</button>
              <button className="topbar-btn icon">🚀</button>
              <button className="topbar-btn primary">⬆ 发布</button>
            </div>
          </div>

          <div className="main">
            <div className="left-toolbar">
              <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 2, paddingTop: 0 }}>
                <div className="tool-btn active">📁</div>
                <div className="tool-btn">🗂</div>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 2 }}>
                <div className="tool-btn">▶</div>
                <div className="tool-btn">◷</div>
                <div style={{
                  marginTop: 8, paddingTop: 8, borderTop: `0.5px solid ${token.colorBorder}`,
                  display: 'flex', alignItems: 'center', justifyContent: 'center'
                }}>
                  <div style={{
                    width: 26, height: 26, borderRadius: '50%', display: 'flex',
                    alignItems: 'center', justifyContent: 'center', fontSize: 14,
                    color: token.colorTextDisabled, cursor: 'pointer' }}>⌘
                  </div>
                </div>
              </div>
            </div>

            <div className="side-panel">
              <div className="panel-tabs">
                <div className="panel-tab active">📁 文件</div>
                <div className="panel-tab">🗂 DDL</div>
              </div>
              <div className="panel-body">
                <div className="file-tree">
                  <div className="folder">▼ 📂 dws</div>
                  <div className="children">
                    <div className="file active">📄 user_funnel.sql</div>
                    <div className="file">📄 order_summary.sql</div>
                  </div>
                  <div className="folder">▶ 📂 dwd</div>
                  <div className="folder">▶ 📂 ods</div>
                  <div className="folder">▶ 📂 dim</div>
                  <div className="file" style={{ marginTop: 4 }}>📄 common_udfs.sql</div>
                </div>
              </div>
            </div>

            <div className="editor-area">
              <div className="editor-tabs">
                <div className="editor-tab active">
                  <span className="dot" /> user_funnel.sql
                </div>
                <div className="editor-tab">order_summary.sql</div>
                <div className="editor-tab" style={{ flex: 1, borderRight: 'none' }} />
              </div>
              <div className="editor-body">
                <div className="editor-gutter">
                  1<br />2<br />3<br />4<br />5<br />6<br />7<br />8<br />9<br />10<br />11<br />12<br />13<br />14
                </div>
                <div className="editor-code">
                  <div><span className="cm">-- Lanting Stream Flink SQL</span></div>
                  <div><span className="cm">-- 实时用户行为漏斗分析</span></div>
                  <div>&nbsp;</div>
                  <div><span className="kw">INSERT INTO</span> dws_user_funnel</div>
                  <div><span className="kw">SELECT</span></div>
                  <div className="cursor-line"><span style={{ paddingLeft: 16 }}>window_start,</span></div>
                  <div><span style={{ paddingLeft: 16 }}>window_end,</span></div>
                  <div><span style={{ paddingLeft: 16 }}>channel,</span></div>
                  <div><span style={{ paddingLeft: 16 }}><span className="kw">COUNT</span>(<span className="kw">DISTINCT</span> user_id) <span className="kw">AS</span> uv,</span></div>
                  <div><span style={{ paddingLeft: 16 }}><span className="kw">SUM</span>(amount) <span className="kw">AS</span> gmv</span></div>
                  <div><span className="kw">FROM</span> <span className="kw">TABLE</span>(<span className="fn">TUMBLE</span>(<span className="kw">TABLE</span> dwd_user_event,</div>
                  <div><span style={{ paddingLeft: 20 }}><span className="kw">DESCRIPTOR</span>(event_time),</span></div>
                  <div><span style={{ paddingLeft: 20 }}><span className="kw">INTERVAL</span> <span className="str">'1'</span> <span className="fn">MINUTE</span>))</span></div>
                  <div><span className="kw">WHERE</span> event_type <span className="kw">IN</span> (<span className="str">'view'</span>, <span className="str">'click'</span>)</div>
                  <div><span className="kw">GROUP BY</span> window_start, window_end, channel</div>
                </div>
              </div>
            </div>

            <div className="right-toolbar">
              <div className="tool-btn">⚙</div>
              <div className="tool-btn">☁</div>
              <div className="tool-btn">◷</div>
              <div className="tool-btn">⏱</div>
            </div>
          </div>

          <div className="bottom-panel">
            <div className="panel-tabs">
              <div className="panel-tab active">▶ 运行记录</div>
              <div className="panel-tab">◷ 版本控制</div>
              <span style={{ flex: 1 }} />
              <span style={{ fontSize: 10, color: token.colorTextDescription, paddingRight: 12 }}>共 12 条记录</span>
            </div>
            <div className="panel-body" style={{ overflowY: 'auto' }}>
              <table className="run-table">
                <thead>
                  <tr><th>记录 ID</th><th>执行时间</th><th>状态</th><th>耗时</th><th>摘要</th></tr>
                </thead>
                <tbody>
                  <tr>
                    <td>#1287</td><td>07-02 09:44:32</td>
                    <td><span className="status-badge status-success">成功</span></td>
                    <td>1.2s</td>
                    <td>INSERT INTO dws_user_funnel …</td>
                  </tr>
                  <tr>
                    <td>#1286</td><td>07-02 09:40:15</td>
                    <td><span className="status-badge status-running">执行中</span></td>
                    <td>—</td>
                    <td>SELECT * FROM dwd_user_event …</td>
                  </tr>
                  <tr>
                    <td>#1285</td><td>07-02 09:38:01</td>
                    <td><span className="status-badge status-failed">失败</span></td>
                    <td>0.3s</td>
                    <td style={{ color: token.colorError }}>Parse Error: Unexpected token …</td>
                  </tr>
                  <tr>
                    <td>#1284</td><td>07-02 09:30:45</td>
                    <td><span className="status-badge status-cancelled">取消</span></td>
                    <td>—</td>
                    <td>INSERT INTO dws_order_summary …</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          <div className="statusbar">
            <div className="breadcrumb">
              <span>工作空间</span><span>/</span><span>任务开发</span><span>/</span>
              <span style={{ color: token.colorTextSecondary, fontWeight: 500 }}>dws/user_funnel.sql</span>
            </div>
            <span className="spacer" />
            <div style={{ fontSize: 11, color: token.colorTextDescription, display: 'flex', gap: 10 }}>
              <span>行 6 列 17</span><span>UTF-8</span><span>缩进: 2</span><span>🔓</span>
            </div>
          </div>
        </div>
      </div>
    </>
  );
};

export default EditorPreview;
