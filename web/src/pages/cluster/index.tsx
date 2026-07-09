import React, { useState, useEffect, useCallback } from 'react';
import { useIntl, useModel } from 'umi';
import { Modal, Form, Input, Select, Switch, Popconfirm, Button, Spin, message } from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  CloudServerOutlined,
  MinusCircleOutlined,
} from '@ant-design/icons';
import {
  listClusters,
  createCluster,
  updateCluster,
  deleteCluster,
  toggleClusterStatus,
  checkFlinkVersion,
} from '@/services/cluster';
import { ApiError } from '@/utils/request';
import type { ClusterVO, CreateClusterDTO, UpdateClusterDTO } from '@/types/cluster';
import type { LantingToken } from '@/themes';

type ModalMode = 'create' | 'edit';

/** 部署目标选项 */
const DEPLOY_TARGETS = [
  { label: 'YARN Session', value: 'yarn-session' },
  { label: 'YARN Application', value: 'yarn-application' },
  { label: 'Kubernetes Session', value: 'kubernetes-session' },
  { label: 'Kubernetes Application', value: 'kubernetes-application' },
  { label: 'Local Mini-cluster', value: 'local' },
  { label: 'Remote', value: 'remote' },
];

/** 资源类型 → Logo 颜色映射 */
const RESOURCE_COLORS: Record<string, { bg: string; text: string }> = {
  YARN:        { bg: 'rgba(225, 126, 48, 0.12)', text: '#C56A1A' },
  KUBERNETES:  { bg: 'rgba(50, 108, 229, 0.12)',  text: '#2554C7' },
  LOCAL:       { bg: 'rgba(123, 135, 148, 0.12)',  text: '#5A6672' },
  REMOTE:      { bg: 'rgba(74, 138, 106, 0.12)',   text: '#3A6A52' },
};

function resourceTypeFromTarget(deployTarget: string): string {
  if (deployTarget.startsWith('yarn')) return 'YARN';
  if (deployTarget.startsWith('kubernetes')) return 'KUBERNETES';
  if (deployTarget === 'local') return 'LOCAL';
  if (deployTarget === 'remote') return 'REMOTE';
  return 'LOCAL';
}

const ClusterPage: React.FC = () => {
  const token = useModel('theme') as LantingToken;
  const { formatMessage } = useIntl();

  // 列表状态
  const [clusters, setClusters] = useState<ClusterVO[]>([]);
  const [loading, setLoading] = useState(false);

  // Modal 状态
  const [modalOpen, setModalOpen] = useState(false);
  const [modalMode, setModalMode] = useState<ModalMode>('create');
  const [editId, setEditId] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  // 版本检测状态
  const [checkingVersion, setCheckingVersion] = useState(false);
  const [detectedVersion, setDetectedVersion] = useState<string | null>(null);

  const [form] = Form.useForm();

  const fetchClusters = useCallback(() => {
    setLoading(true);
    listClusters()
      .then(setClusters)
      .catch((err: Error) => message.error(err.message || formatMessage({ id: 'error.default' })))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => { fetchClusters(); }, [fetchClusters]);

  // ==================== Modal 操作 ====================

  const openCreate = () => {
    setModalMode('create');
    setEditId(null);
    setDetectedVersion(null);
    form.resetFields();
    setModalOpen(true);
  };

  const openEdit = (c: ClusterVO) => {
    setModalMode('edit');
    setEditId(c.id);
    setDetectedVersion(null);
    form.setFieldsValue({
      name: c.name,
      flinkHome: c.flinkHome,
      deployTarget: c.deployTarget,
      configurations: c.configurations,
    });
    setModalOpen(true);
  };

  const handleCheckVersion = async () => {
    const flinkHome = form.getFieldValue('flinkHome');
    if (!flinkHome) return;
    setCheckingVersion(true);
    try {
      const version = await checkFlinkVersion(flinkHome);
      setDetectedVersion(version);
    } catch (err: any) {
      setDetectedVersion(null);
      message.error(err.message || formatMessage({ id: 'error.default' }));
    } finally {
      setCheckingVersion(false);
    }
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      const payload = {
        name: values.name,
        flinkHome: values.flinkHome,
        deployTarget: values.deployTarget,
        configurations: values.configurations || undefined,
      };

      if (modalMode === 'create') {
        await createCluster(payload as CreateClusterDTO);
        message.success('集群创建成功');
      } else if (editId) {
        await updateCluster(editId, { id: editId, ...payload } as UpdateClusterDTO);
        message.success('集群更新成功');
      }
      setModalOpen(false);
      fetchClusters();
    } catch (err: any) {
      // 只处理接口异常，表单校验失败不提示
      if (err instanceof ApiError) {
        message.error(err.message || formatMessage({ id: 'error.default' }));
      }
    } finally {
      setSubmitting(false);
    }
  };

  // ==================== 卡片操作 ====================

  const handleDelete = async (id: string, name: string) => {
    try {
      await deleteCluster(id);
      message.success(`已删除集群「${name}」`);
      fetchClusters();
    } catch (err: any) {
      message.error(err.message || formatMessage({ id: 'error.default' }));
    }
  };

  const handleToggleStatus = async (id: string) => {
    try {
      await toggleClusterStatus(id);
      fetchClusters();
    } catch (err: any) {
      message.error(err.message || formatMessage({ id: 'error.default' }));
    }
  };

  // ==================== RENDER ====================

  return (
    <div>
      {/* 页头 */}
      <div style={{
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        marginBottom: token.spacingXL,
      }}>
        <div>
          <h2 style={{
            fontFamily: 'Georgia, "Noto Serif SC", serif',
            fontSize: token.fontSizeTitle, fontWeight: token.fontWeightMedium,
            color: token.colorText, margin: '0 0 4px',
          }}>
            集群管理
          </h2>
          <div style={{ fontSize: token.fontSizeCaption, color: token.colorTextDescription }}>
            管理 Flink 集群配置，新建、编辑、启停
          </div>
        </div>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
          新建集群
        </Button>
      </div>

      {/* 卡片网格 */}
      <Spin spinning={loading}>
        <div style={{
          display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: token.spacingLG,
        }}>
          {clusters.map((c) => (
            <ClusterCard
              key={c.id}
              cluster={c}
              token={token}
              onEdit={() => openEdit(c)}
              onDelete={() => handleDelete(c.id, c.name)}
              onToggleStatus={() => handleToggleStatus(c.id)}
            />
          ))}
        </div>
      </Spin>

      {/* 空状态 */}
      {!loading && clusters.length === 0 && (
        <div style={{
          textAlign: 'center', padding: `${token.spacing4XL}px 0`,
          color: token.colorTextDescription, fontSize: token.fontSizeBody,
        }}>
          <CloudServerOutlined style={{ fontSize: 32, marginBottom: token.spacingMD, display: 'block' }} />
          暂无集群，点击右上角「新建集群」开始
        </div>
      )}

      {/* 新建/编辑 Modal */}
      <Modal
        title={modalMode === 'create' ? '新建集群' : '编辑集群'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={handleSubmit}
        confirmLoading={submitting}
        destroyOnHidden
        width={420}
      >
        <Form form={form} layout="vertical" style={{ marginTop: token.spacingSM }}>
          <Form.Item
            name="name"
            label="集群名称"
            rules={[{ required: true, message: '请输入集群名称' }]}
          >
            <Input placeholder="例：prod-yarn" />
          </Form.Item>

          <Form.Item
            name="flinkHome"
            label="FLINK_HOME"
            rules={[{ required: true, message: '请输入 FLINK_HOME 路径' }]}
          >
            <Input.Search
              placeholder="/opt/flink"
              enterButton={checkingVersion ? '检测中...' : '检测版本'}
              loading={checkingVersion}
              onSearch={handleCheckVersion}
            />
          </Form.Item>
          {detectedVersion && (
            <div style={{
              fontSize: token.fontSizeCaption, color: token.colorSuccess,
              marginTop: -token.spacingSM, marginBottom: token.spacingSM,
            }}>
              ✓ 检测到 Flink {detectedVersion}
            </div>
          )}

          <Form.Item
            name="deployTarget"
            label="部署目标"
            rules={[{ required: true, message: '请选择部署目标' }]}
          >
            <Select
              placeholder="选择部署目标"
              options={DEPLOY_TARGETS}
              onChange={() => setDetectedVersion(null)}
            />
          </Form.Item>

          <Form.Item name="configurations" label="配置信息（可选）">
            <Input.TextArea rows={3} placeholder='JSON 格式，例：{"parallelism": 4}' />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

// ==================== 子组件：集群卡片 ====================

interface ClusterCardProps {
  cluster: ClusterVO;
  token: LantingToken;
  onEdit: () => void;
  onDelete: () => void;
  onToggleStatus: () => void;
}

const ClusterCard: React.FC<ClusterCardProps> = ({ cluster, token, onEdit, onDelete, onToggleStatus }) => {
  const isActive = cluster.status === 'ACTIVE';
  const resourceType = cluster.resourceType || resourceTypeFromTarget(cluster.deployTarget);
  const colors = RESOURCE_COLORS[resourceType] ?? RESOURCE_COLORS.LOCAL;

  return (
    <div style={{
      background: token.colorBgContainer,
      border: `0.5px solid ${token.colorBorder}`,
      borderRadius: token.borderRadius,
      padding: `${token.spacingLG}px`,
      opacity: isActive ? 1 : 0.45,
      transition: 'opacity 0.2s',
      display: 'flex', flexDirection: 'column',
    }}>
      <div style={{ display: 'flex', alignItems: 'flex-start', marginBottom: token.spacingSM }}>
        <div style={{
          width: 36, height: 36, borderRadius: token.borderRadius,
          background: colors.bg, color: colors.text,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          fontSize: 11, fontWeight: 500, flexShrink: 0,
          marginRight: token.spacingMD,
        }}>
          {resourceType.slice(0, 3)}
        </div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontSize: 15, fontWeight: 500, color: token.colorText, lineHeight: 1.3 }}>
            {cluster.name}
          </div>
          <div style={{ fontSize: token.fontSizeCaption, color: token.colorTextDescription, marginTop: 2 }}>
            {resourceType} · {cluster.deployTarget.replace('-', ' ')}
          </div>
        </div>
        <Switch checked={isActive} onChange={onToggleStatus} size="small" />
      </div>

      <div style={{
        fontSize: token.fontSizeCaption, color: token.colorTextDescription,
        paddingLeft: 48, marginBottom: token.spacingSM,
      }}>
        Flink {cluster.flinkVersion || '未知'}
      </div>

      {isActive && (
        <div style={{
          display: 'flex', alignItems: 'center', gap: 6,
          fontSize: token.fontSizeCaption, color: token.colorTextDescription,
          paddingLeft: 48, marginBottom: token.spacingMD,
        }}>
          <MinusCircleOutlined style={{ fontSize: 7, color: token.colorTextDisabled }} />
          未知（未检测连通性）
        </div>
      )}

      <div style={{
        display: 'flex', borderTop: `0.5px solid ${token.colorBgMuted}`,
        margin: `0 -${token.spacingLG}px -${token.spacingLG}px`,
        marginTop: 'auto',
      }}>
        <button disabled={!isActive} onClick={onEdit} style={actionBtnStyle(token, isActive)}>
          <EditOutlined />
        </button>
        <Popconfirm
          title={`确认删除集群「${cluster.name}」？`}
          description="删除后不可恢复"
          onConfirm={onDelete}
          okText="确认删除"
          cancelText="取消"
        >
          <button disabled={!isActive} style={actionBtnStyle(token, isActive)}>
            <DeleteOutlined />
          </button>
        </Popconfirm>
      </div>
    </div>
  );
};

function actionBtnStyle(token: LantingToken, enabled: boolean): React.CSSProperties {
  return {
    flex: 1, height: 40,
    border: 'none', borderRight: `0.5px solid ${token.colorBgMuted}`,
    background: 'transparent', fontSize: 16,
    color: enabled ? token.colorTextSecondary : token.colorTextDisabled,
    cursor: enabled ? 'pointer' : 'not-allowed',
    display: 'flex', alignItems: 'center', justifyContent: 'center',
    transition: 'background 0.15s, color 0.15s',
  };
}

export default ClusterPage;
