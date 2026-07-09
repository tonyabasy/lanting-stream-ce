import React from 'react';
import { useModel } from 'umi';
import {
  CheckCircleOutlined,
  WarningOutlined,
  CloseCircleOutlined,
  ThunderboltOutlined,
  ClusterOutlined,
  CodeOutlined,
} from '@ant-design/icons';
import type { LantingToken } from '@/themes';

/** 集群状态卡片 */
const clusters = [
  { name: 'prod-yarn', type: 'YARN Session', flink: '2.2.0', status: 'healthy' as const, cpu: '16/32', mem: '24/64' },
  { name: 'staging-flink', type: 'YARN Application', flink: '2.2.0', status: 'healthy' as const, cpu: '6/16', mem: '12/32' },
  { name: 'test-k8s', type: 'K8s Application', flink: '2.2.0', status: 'offline' as const, cpu: '-', mem: '-' },
  { name: 'dev-local', type: 'Local Mini-cluster', flink: '2.2.0', status: 'healthy' as const, cpu: '2/4', mem: '4/8' },
];

/** 作业统计 */
const jobStats = [
  { label: '运行中', value: 8, colorKey: 'success' as const, icon: <ThunderboltOutlined /> },
  { label: '异常', value: 2, colorKey: 'error' as const, icon: <WarningOutlined /> },
  { label: '已停止', value: 4, colorKey: 'disabled' as const, icon: <CloseCircleOutlined /> },
];

const recentJobs = [
  { name: 'dws_user_count', status: 'running' as const, uptime: '3h 22m', checkpoint: '正常' },
  { name: 'dws_gmv_realtime', status: 'warning' as const, uptime: '5h 10m', checkpoint: '延迟 2min' },
  { name: 'ods_order_sync', status: 'running' as const, uptime: '12h 5m', checkpoint: '正常' },
  { name: 'dwd_event_clean', status: 'stopped' as const, uptime: '-', checkpoint: '-' },
];

const OpsPage: React.FC = () => {
  const token = useModel('theme') as LantingToken;

  /** 根据 colorKey 取对应 color + bg */
  const colorPair = (key: 'success' | 'error' | 'disabled') => {
    const map = {
      success: { color: token.colorSuccess, bg: token.colorSuccessBg },
      error: { color: token.colorError, bg: '#FCEBEB' },
      disabled: { color: token.colorTextDescription, bg: '#F5F5F5' },
    };
    return map[key];
  };

  return (
    <div>
      {/* 页头 */}
      <div style={{ marginBottom: token.sizeXL }}>
        <h2
          style={{
            fontFamily: 'Georgia, "Noto Serif SC", serif',
            fontSize: token.fontSizeTitle,
            fontWeight: token.fontWeightMedium,
            color: token.colorText,
            margin: '0 0 4px',
          }}
        >
          运维中心
        </h2>
        <div style={{ fontSize: token.fontSizeCaption, color: token.colorTextDescription }}>
          实时监控集群与作业运行状态
        </div>
      </div>

      {/* 统计卡片 */}
      <div style={{ display: 'flex', gap: token.sizeMD, marginBottom: token.sizeXL }}>
        {jobStats.map((s) => {
          const { color } = colorPair(s.colorKey);
          return (
            <div
              key={s.label}
              style={{
                flex: 1,
                background: token.colorBgSubtle,
                border: `0.5px solid ${token.colorBorder}`,
                borderRadius: token.borderRadius,
                padding: `${token.sizeMD}px ${token.sizeLG}px`,
                display: 'flex',
                alignItems: 'center',
                gap: token.sizeMD,
              }}
            >
              <div
                style={{
                  width: 36,
                  height: 36,
                  borderRadius: token.borderRadius,
                  background: s.colorKey === 'success'
                    ? token.colorSuccessBg
                    : s.colorKey === 'disabled'
                      ? token.colorBgSubtle
                      : '#FCEBEB',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: token.fontSizeTitle,
                  color,
                }}
              >
                {s.icon}
              </div>
              <div>
                <div style={{ fontSize: token.fontSizeHeading, fontWeight: token.fontWeightMedium, color: token.colorText, lineHeight: 1 }}>
                  {s.value}
                </div>
                <div style={{ fontSize: token.fontSizeCaption, color: token.colorTextDescription, marginTop: token.sizeXS }}>
                  {s.label}
                </div>
              </div>
            </div>
          );
        })}
      </div>

      {/* 集群列表 */}
      <div
        style={{
          background: token.colorBgContainer,
          border: `0.5px solid ${token.colorBorder}`,
          borderRadius: token.borderRadius,
          marginBottom: token.sizeXL,
          overflow: 'hidden',
        }}
      >
        <div
          style={{
            padding: `${token.sizeMD}px ${token.sizeLG}px`,
            borderBottom: `0.5px solid ${token.colorBorder}`,
            fontSize: 13,
            fontWeight: token.fontWeightMedium,
            color: token.colorText,
            display: 'flex',
            alignItems: 'center',
            gap: token.sizeSM,
          }}
        >
          <ClusterOutlined style={{ color: token.colorPrimary }} />
          集群状态
        </div>
        <div style={{ padding: `${token.sizeMD}px ${token.sizeLG}px` }}>
          {clusters.map((c) => {
            const isHealthy = c.status === 'healthy';
            return (
              <div
                key={c.name}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: token.sizeMD,
                  padding: '10px 0',
                  borderBottom: `0.5px solid ${token.colorBgMuted}`,
                  fontSize: token.fontSizeCaption,
                }}
              >
                <span style={{ flex: 1, fontWeight: token.fontWeightMedium, color: token.colorText }}>
                  {c.name}
                </span>
                <span style={{ color: token.colorTextDescription, flex: 1 }}>{c.type}</span>
                <span style={{ color: token.colorTextDescription, width: 70 }}>Flink {c.flink}</span>
                <span style={{ color: token.colorTextSecondary, width: 80 }}>CPU {c.cpu} / MEM {c.mem}</span>
                <span
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: token.sizeXS,
                    padding: '2px 8px',
                    borderRadius: token.borderRadiusSM,
                    fontSize: token.fontSizeCaption,
                    background: isHealthy ? token.colorSuccessBg : '#FCEBEB',
                    color: isHealthy ? token.colorSuccess : token.colorError,
                  }}
                >
                  {isHealthy ? <CheckCircleOutlined /> : <CloseCircleOutlined />}
                  {isHealthy ? '正常' : '离线'}
                </span>
              </div>
            );
          })}
        </div>
      </div>

      {/* 作业列表 */}
      <div
        style={{
          background: token.colorBgContainer,
          border: `0.5px solid ${token.colorBorder}`,
          borderRadius: token.borderRadius,
          overflow: 'hidden',
        }}
      >
        <div
          style={{
            padding: `${token.sizeMD}px ${token.sizeLG}px`,
            borderBottom: `0.5px solid ${token.colorBorder}`,
            fontSize: 13,
            fontWeight: token.fontWeightMedium,
            color: token.colorText,
            display: 'flex',
            alignItems: 'center',
            gap: token.sizeSM,
          }}
        >
          <CodeOutlined style={{ color: token.colorPrimary }} />
          近期作业
        </div>
        <div style={{ padding: `${token.sizeMD}px ${token.sizeLG}px` }}>
          {recentJobs.map((j) => {
            const badge =
              j.status === 'running'
                ? { bg: token.colorSuccessBg, color: token.colorSuccess, label: '运行中' }
                : j.status === 'warning'
                  ? { bg: token.colorWarningBg, color: token.colorWarning, label: '异常' }
                  : { bg: '#F5F5F5', color: token.colorTextDescription, label: '已停止' };
            return (
              <div
                key={j.name}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: token.sizeMD,
                  padding: '9px 0',
                  borderBottom: `0.5px solid ${token.colorBgMuted}`,
                  fontSize: token.fontSizeCaption,
                }}
              >
                <span style={{ flex: 1, fontWeight: token.fontWeightMedium, color: token.colorText }}>
                  {j.name}
                </span>
                <span style={{ color: token.colorTextDescription, width: 80 }}>运行 {j.uptime}</span>
                <span style={{ color: token.colorTextSecondary, width: 80 }}>Checkpoint: {j.checkpoint}</span>
                <span
                  style={{
                    padding: '2px 8px',
                    borderRadius: token.borderRadiusSM,
                    fontSize: token.fontSizeCaption,
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
