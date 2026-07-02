import React from 'react';
import { CodeOutlined, WarningOutlined, CheckCircleOutlined } from '@ant-design/icons';

const statCards = [
  { label: '在线作业', value: 12, trend: '↑ 2 今日', trendColor: 'var(--color-primary)' },
  { label: '异常作业', value: 1,  trend: '需处理',   trendColor: 'var(--color-error)' },
  { label: 'Flink 集群', value: 2, trend: '全部健康', trendColor: 'var(--color-success)' },
];

const jobs = [
  { name: 'dws_user_count',   cluster: 'prod-k8s', status: 'running', label: '运行中' },
  { name: 'dws_gmv_realtime', cluster: 'prod-k8s', status: 'warning', label: '反压' },
  { name: 'ods_order_sync',   cluster: 'dev',      status: 'stopped', label: '已停止' },
  { name: 'dwd_event_clean',  cluster: 'prod-k8s', status: 'running', label: '运行中' },
];

const badgeStyle: Record<string, React.CSSProperties> = {
  running: { background: 'var(--color-success-bg)', color: 'var(--color-success)' },
  warning: { background: 'var(--color-warning-bg)', color: 'var(--color-warning)' },
  stopped: { background: 'var(--color-bg-muted)', color: 'var(--color-text-description)', border: '0.5px solid var(--color-border)' },
};

const dotColor: Record<string, string> = {
  running: 'var(--color-success)',
  warning: 'var(--color-warning)',
  stopped: 'var(--color-neutral)',
};

const HomePage: React.FC = () => (
  <div>
    {/* 页面标题 */}
    <div style={{ marginBottom: 20 }}>
      <div style={{ fontSize: 16, fontWeight: 500, color: 'var(--color-text)', fontFamily: 'var(--font-family)', marginBottom: 3 }}>
        概览
      </div>
      <div style={{ fontSize: 12, color: 'var(--color-text-description)' }}>今日 · prod 工作空间</div>
    </div>

    {/* 统计卡片 */}
    <div style={{ display: 'flex', gap: 12, marginBottom: 16 }}>
      {statCards.map(s => (
        <div key={s.label} style={{ flex: 1, background: 'var(--color-bg-subtle)', borderRadius: 10, padding: '11px 13px' }}>
          <div style={{ fontSize: 11, color: 'var(--color-text-description)', marginBottom: 5 }}>{s.label}</div>
          <div style={{ fontSize: 22, fontWeight: 500, color: 'var(--color-text)', lineHeight: 1 }}>{s.value}</div>
          <div style={{ fontSize: 10, color: s.trendColor, marginTop: 4 }}>{s.trend}</div>
        </div>
      ))}
    </div>

    {/* 快捷操作 */}
    <div style={{
      background: 'var(--color-bg-container)', border: '0.5px solid var(--color-border)', borderRadius: 10,
      padding: '10px 12px', display: 'flex', alignItems: 'center', gap: 8,
      cursor: 'pointer', marginBottom: 16,
    }}>
      <CodeOutlined style={{ fontSize: 15, color: 'var(--color-primary)' }} />
      <div>
        <div style={{ fontSize: 12, fontWeight: 500, color: 'var(--color-text)' }}>新建作业</div>
        <div style={{ fontSize: 10, color: 'var(--color-text-description)', marginTop: 1 }}>AI 生成 SQL</div>
      </div>
    </div>

    {/* 作业列表 */}
    <div style={{ background: 'var(--color-bg-container)', border: '0.5px solid var(--color-border)', borderRadius: 10, padding: '13px 15px', marginBottom: 14 }}>
      <div style={{
        fontSize: 12, fontWeight: 500, color: 'var(--color-text)',
        fontFamily: 'var(--font-family)', marginBottom: 10,
        display: 'flex', justifyContent: 'space-between',
      }}>
        作业列表
        <span style={{ fontSize: 11, color: 'var(--color-primary)', fontFamily: 'var(--font-family)', fontWeight: 400, cursor: 'pointer' }}>全部 →</span>
      </div>
      {jobs.map(job => (
        <div key={job.name} style={{
          display: 'flex', alignItems: 'center', gap: 8,
          padding: '6px 0', borderBottom: '0.5px solid var(--color-border)', fontSize: 12,
        }}>
          <div style={{ width: 6, height: 6, borderRadius: '50%', background: dotColor[job.status], flexShrink: 0 }} />
          <span style={{ flex: 1, color: 'var(--color-text)', fontWeight: 500 }}>{job.name}</span>
          <span style={{ color: 'var(--color-text-description)', fontSize: 11 }}>{job.cluster}</span>
          <span style={{ fontSize: 10, padding: '2px 6px', borderRadius: 4, ...badgeStyle[job.status] }}>{job.label}</span>
        </div>
      ))}
    </div>

    {/* AI 洞察 */}
    <div style={{ background: 'var(--color-bg-container)', border: '0.5px solid var(--color-border)', borderRadius: 10, padding: '13px 15px' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 5, marginBottom: 8 }}>
        <div style={{ width: 6, height: 6, borderRadius: '50%', background: 'var(--color-primary)' }} />
        <span style={{ fontSize: 12, fontWeight: 500, color: 'var(--color-text)', fontFamily: 'var(--font-family)' }}>AI 洞察</span>
      </div>
      <div style={{ fontSize: 11, color: 'var(--color-text-secondary)', lineHeight: 1.7, fontFamily: 'var(--font-family)', marginBottom: 5 }}>
        <WarningOutlined style={{ color: 'var(--color-warning)', fontSize: 12 }} />
        {' '}dws_gmv_realtime 检测到持续反压，建议并行度 4→8，预计可消除积压。
      </div>
      <div style={{ fontSize: 11, color: 'var(--color-text-secondary)', lineHeight: 1.7, fontFamily: 'var(--font-family)' }}>
        <CheckCircleOutlined style={{ color: 'var(--color-success)', fontSize: 12 }} />
        {' '}今日 SQL 提交 8 次，其中 7 次通过 AI Review，平均执行计划优化率 23%。
      </div>
    </div>
  </div>
);

export default HomePage;
