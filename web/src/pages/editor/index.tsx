import React, { useState } from 'react';
import { CaretRightOutlined, SaveOutlined, BulbOutlined } from '@ant-design/icons';

const SQL = `-- 实时用户行为漏斗统计
INSERT INTO dws_user_funnel
SELECT
  window_start,
  window_end,
  channel,
  COUNT(DISTINCT user_id)  AS uv,
  COUNT(*)                 AS pv,
  COUNT(DISTINCT order_id) AS order_cnt,
  SUM(amount)              AS gmv
FROM TABLE(
  TUMBLE(
    TABLE ods_user_event,
    DESCRIPTOR(event_time),
    INTERVAL '1' MINUTE
  )
)
WHERE event_type IN ('view', 'click', 'order')
GROUP BY
  window_start,
  window_end,
  channel`;

const KEYWORDS = [
  'INSERT INTO', 'SELECT', 'FROM', 'WHERE', 'GROUP BY',
  'COUNT', 'SUM', 'DISTINCT', 'TABLE', 'INTERVAL',
  'DESCRIPTOR', 'IN', 'AS',
];

const RESULTS = [
  { window: '10:01:00', channel: 'app', uv: '1,234', pv: '8,920', orders: '342', gmv: '¥28,450' },
  { window: '10:01:00', channel: 'web', uv: '892',   pv: '4,310', orders: '178', gmv: '¥15,230' },
  { window: '10:02:00', channel: 'app', uv: '1,108', pv: '7,640', orders: '298', gmv: '¥24,860' },
];

type ResultTab = 'result' | 'log' | 'plan';

function renderLine(line: string, idx: number) {
  if (line.trimStart().startsWith('--')) {
    return (
      <div key={idx} style={{ color: 'var(--color-text-description)', fontStyle: 'italic' }}>{line || '\u00A0'}</div>
    );
  }
  const parts: React.ReactNode[] = [];
  let remaining = line;
  let i = 0;
  while (remaining.length > 0) {
    let matched = false;
    for (const kw of KEYWORDS) {
      if (remaining.startsWith(kw)) {
        parts.push(<span key={i++} style={{ color: 'var(--color-primary)', fontWeight: 500 }}>{kw}</span>);
        remaining = remaining.slice(kw.length);
        matched = true;
        break;
      }
    }
    if (!matched) {
      if (remaining.startsWith("'")) {
        const end = remaining.indexOf("'", 1);
        const str = end >= 0 ? remaining.slice(0, end + 1) : remaining;
        parts.push(<span key={i++} style={{ color: 'var(--color-success)' }}>{str}</span>);
        remaining = end >= 0 ? remaining.slice(end + 1) : '';
      } else {
        parts.push(<span key={i++}>{remaining[0]}</span>);
        remaining = remaining.slice(1);
      }
    }
  }
  return <div key={idx}>{parts.length ? parts : '\u00A0'}</div>;
}

const EditorPage: React.FC = () => {
  const [activeTab, setActiveTab] = useState<ResultTab>('result');
  const [aiMenuVisible, setAiMenuVisible] = useState(false);
  const lines = SQL.split('\n');

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', margin: '-20px -22px' }}>

      {/* 文件标签栏 */}
      <div style={{
        display: 'flex', alignItems: 'flex-end',
        padding: '0 14px', gap: 2,
        background: 'var(--color-bg-container)', borderBottom: '0.5px solid var(--color-border)', flexShrink: 0,
      }}>
        <div style={{
          padding: '7px 14px', borderRadius: '5px 5px 0 0',
          border: '0.5px solid var(--color-border)', borderBottom: 'none',
          background: 'var(--color-primary-bg)', color: 'var(--color-primary)',
          fontSize: 12, fontFamily: 'var(--font-family-code)',
        }}>
          dws_user_funnel.sql
        </div>
        <div style={{
          padding: '7px 14px', borderRadius: '5px 5px 0 0',
          border: '0.5px solid transparent',
          color: 'var(--color-text-description)', fontSize: 12, fontFamily: 'var(--font-family-code)', cursor: 'pointer',
        }}>
          ods_user_event.sql
        </div>
        <div style={{ flex: 1 }} />
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, paddingBottom: 7 }}>
          <SaveOutlined style={{ fontSize: 14, color: 'var(--color-text-description)', cursor: 'pointer' }} />
          <div style={{
            display: 'flex', alignItems: 'center', gap: 5,
            padding: '4px 12px', borderRadius: 6,
            background: 'var(--color-primary)', color: 'var(--color-text-light-solid)',
            fontSize: 12, fontWeight: 500, cursor: 'pointer',
          }}>
            <CaretRightOutlined style={{ fontSize: 11 }} />
            运行
          </div>
        </div>
      </div>

      {/* 编辑器主体 */}
      <div style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>
        {/* 行号 */}
        <div style={{
          padding: '14px 10px', background: 'var(--color-bg-subtle)',
          borderRight: '0.5px solid var(--color-border)',
          fontSize: 12, color: 'var(--color-text-disabled)',
          fontFamily: 'var(--font-family-code)', lineHeight: '20px',
          textAlign: 'right', minWidth: 38, flexShrink: 0, userSelect: 'none',
        }}>
          {lines.map((_, i) => <div key={i}>{i + 1}</div>)}
        </div>

        {/* 代码区 */}
        <div style={{ flex: 1, overflow: 'auto', position: 'relative' }}>
          <div style={{
            padding: '14px 16px', fontSize: 13,
            fontFamily: 'var(--font-family-code)', lineHeight: '20px',
            color: 'var(--color-text)', minWidth: 'max-content',
          }}>
            {lines.map((line, i) => renderLine(line, i))}
          </div>

          {/* AI 浮动操作 */}
          <div
            onMouseEnter={() => setAiMenuVisible(true)}
            onMouseLeave={() => setAiMenuVisible(false)}
            style={{ position: 'absolute', top: 14, right: 16 }}
          >
            {aiMenuVisible ? (
              <div style={{
                background: 'var(--color-bg-container)', border: '0.5px solid var(--color-border)',
                borderRadius: 6, padding: 4, display: 'flex', gap: 2,
                boxShadow: '0 4px 16px rgba(0,0,0,0.08)',
              }}>
                {['AI 解释', '优化', 'Review', '生成测试数据'].map((action, idx) => (
                  <div key={action} style={{
                    background: idx === 0 ? 'var(--color-primary-bg)' : 'transparent',
                    color: idx === 0 ? 'var(--color-primary)' : 'var(--color-text-secondary)',
                    fontSize: 11, padding: '3px 8px', borderRadius: 4,
                    cursor: 'pointer', whiteSpace: 'nowrap',
                  }}>
                    {action}
                  </div>
                ))}
              </div>
            ) : (
              <div style={{
                width: 26, height: 26, borderRadius: 6,
                background: 'var(--color-primary-bg)',
                display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer',
              }}>
                <BulbOutlined style={{ fontSize: 13, color: 'var(--color-primary)' }} />
              </div>
            )}
          </div>
        </div>
      </div>

      {/* 结果面板 */}
      <div style={{ height: 200, borderTop: '0.5px solid var(--color-border)', flexShrink: 0, display: 'flex', flexDirection: 'column' }}>
        <div style={{
          display: 'flex', alignItems: 'center', padding: '0 16px',
          borderBottom: '0.5px solid var(--color-border)', background: 'var(--color-bg-container)', flexShrink: 0,
        }}>
          {(['result', 'log', 'plan'] as ResultTab[]).map(tab => (
            <div
              key={tab}
              onClick={() => setActiveTab(tab)}
              style={{
                padding: '7px 12px', fontSize: 12, cursor: 'pointer',
                color: activeTab === tab ? 'var(--color-primary)' : 'var(--color-text-description)',
                borderBottom: `2px solid ${activeTab === tab ? 'var(--color-primary)' : 'transparent'}`,
                marginBottom: -1,
              }}
            >
              {{ result: '结果', log: '日志', plan: '执行计划' }[tab]}
            </div>
          ))}
          <div style={{ flex: 1 }} />
          <span style={{ fontSize: 11, color: 'var(--color-text-description)' }}>3 行 · 实时更新中</span>
        </div>

        <div style={{ flex: 1, overflow: 'auto', padding: '8px 16px' }}>
          {activeTab === 'result' && (
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12 }}>
              <thead>
                <tr>
                  {['window_end', 'channel', 'uv', 'pv', 'order_cnt', 'gmv'].map(col => (
                    <th key={col} style={{
                      padding: '5px 10px', textAlign: 'left',
                      color: 'var(--color-text-secondary)', fontWeight: 500, background: 'var(--color-bg-subtle)',
                      borderBottom: '0.5px solid var(--color-border)',
                      fontFamily: 'var(--font-family-code)', whiteSpace: 'nowrap',
                    }}>{col}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {RESULTS.map((r, i) => (
                  <tr key={i} style={{ borderBottom: '0.5px solid var(--color-border)' }}>
                    <td style={{ padding: '5px 10px', fontFamily: 'var(--font-family-code)' }}>{r.window}</td>
                    <td style={{ padding: '5px 10px' }}>{r.channel}</td>
                    <td style={{ padding: '5px 10px', fontFamily: 'var(--font-family-code)' }}>{r.uv}</td>
                    <td style={{ padding: '5px 10px', fontFamily: 'var(--font-family-code)' }}>{r.pv}</td>
                    <td style={{ padding: '5px 10px', fontFamily: 'var(--font-family-code)' }}>{r.orders}</td>
                    <td style={{ padding: '5px 10px', fontFamily: 'var(--font-family-code)', color: 'var(--color-primary)', fontWeight: 500 }}>{r.gmv}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
          {activeTab === 'log' && (
            <div style={{ fontFamily: 'var(--font-family-code)', fontSize: 12, lineHeight: 1.8, color: 'var(--color-text-secondary)' }}>
              <div><span style={{ color: 'var(--color-success)' }}>[INFO]</span>  10:01:05.123  作业启动成功，并行度 4</div>
              <div><span style={{ color: 'var(--color-success)' }}>[INFO]</span>  10:01:05.456  Source: ods_user_event 连接建立</div>
              <div><span style={{ color: 'var(--color-warning)' }}>[WARN]</span>  10:01:06.789  检测到轻微反压，缓冲区使用率 72%</div>
              <div><span style={{ color: 'var(--color-success)' }}>[INFO]</span>  10:02:00.001  第一个 Tumble 窗口关闭，输出 3 条记录</div>
            </div>
          )}
          {activeTab === 'plan' && (
            <div style={{ fontFamily: 'var(--font-family-code)', fontSize: 12, lineHeight: 1.8, color: 'var(--color-text-secondary)' }}>
              <div style={{ color: 'var(--color-primary)' }}>Sink</div>
              <div>&nbsp;&nbsp;└─ <span style={{ color: 'var(--color-text)' }}>GroupWindowAggregate</span>  (window=TUMBLE(1 MIN))</div>
              <div>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;└─ <span style={{ color: 'var(--color-text)' }}>Calc</span>  (where: event_type IN (...))</div>
              <div>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;└─ <span style={{ color: 'var(--color-text)' }}>Source</span>: ods_user_event</div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default EditorPage;
