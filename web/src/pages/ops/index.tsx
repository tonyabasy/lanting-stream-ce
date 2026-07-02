import React from 'react';
import {
  CheckCircleOutlined,
  WarningOutlined,
  CloseCircleOutlined,
  ThunderboltOutlined,
  ClusterOutlined,
  CodeOutlined,
} from '@ant-design/icons';

/** 集群状态卡片 */
const clusters = [
  { name: 'prod-yarn', type: 'YARN Session', flink: '2.2.0', status: 'healthy', cpu: '16/32', mem: '24/64' },
  { name: 'staging-flink', type: 'YARN Application', flink: '2.2.0', status: 'healthy', cpu: '6/16', mem: '12/32' },
  { name: 'test-k8s', type: 'K8s Application', flink: '2.2.0', status: 'offline', cpu: '-', mem: '-' },
  { name: 'dev-local', type: 'Local Mini-cluster', flink: '2.2.0', status: 'healthy', cpu: '2/4', mem: '4/8' },
];

const statusMeta: Record<string, { color: string; bg: string; label: string; icon: React.ReactNode }> = {
  healthy: { color: '#2F9A45', bg: '#EAF3DE', label: '正常', icon: <CheckCircleOutlined /> },
  offline: { color: '#9C2E24', bg: '#FCEBEB', label: '离线', icon: <CloseCircleOutlined /> },
};

/** 作业统计 */
const jobStats = [
  { label: '运行中', value: 8, color: '#2F9A45', icon: <ThunderboltOutlined /> },
  { label: '异常', value: 2, color: '#9C2E24', icon: <WarningOutlined /> },
  { label: '已停止', value: 4, color: '#999', icon: <CloseCircleOutlined /> },
];

const recentJobs = [
  { name: 'dws_user_count', status: 'running', uptime: '3h 22m', checkpoint: '正常' },
  { name: 'dws_gmv_realtime', status: 'warning', uptime: '5h 10m', checkpoint: '延迟 2min' },
  { name: 'ods_order_sync', status: 'running', uptime: '12h 5m', checkpoint: '正常' },
  { name: 'dwd_event_clean', status: 'stopped', uptime: '-', checkpoint: '-' },
];

const jobBadge: Record<string, { bg: string; color: string; label: string }> = {
  running: { bg: '#EAF3DE', color: '#2F9A45', label: '运行中' },
  warning: { bg: '#FAEEDA', color: '#A97A1D', label: '异常' },
  stopped: { bg: '#F5F5F5', color: '#999', label: '已停止' },
};

const OpsPage: React.FC = () => {
  return (
    <div>
      {/* 页头 */}
      <div style={{ marginBottom: 22 }}>
        <h2 style={{ fontFamily: 'Georgia, "Noto Serif SC", serif', fontSize: 16, fontWeight: 500, color: '#111', margin: '0 0 4px' }}>
          运维中心
        </h2>
        <div style={{ fontSize: 12, color: '#999' }}>实时监控集群与作业运行状态</div>
      </div>

      {/* 统计卡片 */}
      <div style={{ display: 'flex', gap: 12, marginBottom: 20 }}>
        {jobStats.map((s) => (
          <div
            key={s.label}
            style={{
              flex: 1,
              background: '#FBFCFD',
              border: '0.5px solid #E5E5E5',
              borderRadius: 8,
              padding: '14px 16px',
              display: 'flex',
              alignItems: 'center',
              gap: 12,
            }}
          >
            <div
              style={{
                width: 36,
                height: 36,
                borderRadius: 8,
                background: `${s.color}14`,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: 16,
                color: s.color,
              }}
            >
              {s.icon}
            </div>
            <div>
              <div style={{ fontSize: 20, fontWeight: 600, color: '#111', lineHeight: 1 }}>{s.value}</div>
              <div style={{ fontSize: 11, color: '#999', marginTop: 3 }}>{s.label}</div>
            </div>
          </div>
        ))}
      </div>

      {/* 集群列表 */}
      <div
        style={{
          background: '#FBFCFD',
          border: '0.5px solid #E5E5E5',
          borderRadius: 8,
          marginBottom: 20,
          overflow: 'hidden',
        }}
      >
        <div
          style={{
            padding: '13px 18px',
            borderBottom: '0.5px solid #E5E5E5',
            fontSize: 13,
            fontWeight: 500,
            color: '#111',
            display: 'flex',
            alignItems: 'center',
            gap: 6,
          }}
        >
          <ClusterOutlined style={{ color: '#2A5CA0' }} />
          集群状态
        </div>
        <div style={{ padding: '12px 18px' }}>
          {clusters.map((c) => {
            const meta = statusMeta[c.status];
            return (
              <div
                key={c.name}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 12,
                  padding: '10px 0',
                  borderBottom: '0.5px solid #F0F0F0',
                  fontSize: 12,
                }}
              >
                <span style={{ flex: 1, fontWeight: 500, color: '#111' }}>{c.name}</span>
                <span style={{ color: '#999', flex: 1 }}>{c.type}</span>
                <span style={{ color: '#999', width: 70 }}>Flink {c.flink}</span>
                <span style={{ color: '#555', width: 80 }}>CPU {c.cpu} / MEM {c.mem}</span>
                <span
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: 4,
                    padding: '2px 8px',
                    borderRadius: 4,
                    fontSize: 11,
                    background: meta.bg,
                    color: meta.color,
                  }}
                >
                  {meta.icon}
                  {meta.label}
                </span>
              </div>
            );
          })}
        </div>
      </div>

      {/* 作业列表 */}
      <div
        style={{
          background: '#FBFCFD',
          border: '0.5px solid #E5E5E5',
          borderRadius: 8,
          overflow: 'hidden',
        }}
      >
        <div
          style={{
            padding: '13px 18px',
            borderBottom: '0.5px solid #E5E5E5',
            fontSize: 13,
            fontWeight: 500,
            color: '#111',
            display: 'flex',
            alignItems: 'center',
            gap: 6,
          }}
        >
          <CodeOutlined style={{ color: '#2A5CA0' }} />
          近期作业
        </div>
        <div style={{ padding: '12px 18px' }}>
          {recentJobs.map((j) => {
            const badge = jobBadge[j.status];
            return (
              <div
                key={j.name}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 12,
                  padding: '9px 0',
                  borderBottom: '0.5px solid #F0F0F0',
                  fontSize: 12,
                }}
              >
                <span style={{ flex: 1, fontWeight: 500, color: '#111' }}>{j.name}</span>
                <span style={{ color: '#999', width: 80 }}>运行 {j.uptime}</span>
                <span style={{ color: '#555', width: 80 }}>Checkpoint: {j.checkpoint}</span>
                <span
                  style={{
                    padding: '2px 8px',
                    borderRadius: 4,
                    fontSize: 11,
                    background: badge.bg,
                    color: badge.color,
                  }}
                >
                  {badge.label}
                </span>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
};

export default OpsPage;
