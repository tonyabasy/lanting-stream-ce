import { useState, useRef, useEffect } from 'react';
import { Input, Modal, message } from 'antd';
import { useModel } from 'umi';
import type { FileTreeViewKey } from '@/types/file';
import FileTreeHeader, { buildViewOptions } from './FileTreeHeader';
import FileTreeSearch from './FileTreeSearch';
import FileTreeContent from './FileTreeContent';
import './index.css';

/**
 * 文件树组件（视图下拉切换）。
 *
 * 视图由 fileTree model 的 viewKey 决定（下拉切换），挂载时 switchTreeView 拉对应根路径数据。
 */
const FileTree: React.FC = () => {
  const model = useModel('fileTree');
  const { refresh, collapseAll, createFileNode, createFolderNode, selectedNode, switchTreeView, viewKey, treeData } = model;
  const { setLeftTop } = useModel('devPanels');

  const view: FileTreeViewKey = viewKey;

  // 挂载时若全局树数据为空才加载（面板重开时 treeData/展开状态仍在全局 model，直接复用，不重置）
  // 视图切换由 Header 下拉触发 switchTreeView，组件常驻时不会重跑本 effect
  useEffect(() => {
    if (treeData.length === 0) {
      switchTreeView(viewKey);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 通用输入弹窗状态
  const [modalVisible, setModalVisible] = useState(false);
  const [modalTitle, setModalTitle] = useState('');
  const [modalValue, setModalValue] = useState('');
  const [modalLoading, setModalLoading] = useState(false);
  const modalActionRef = useRef<((val: string) => Promise<void>) | null>(null);

  const openInputModal = (title: string, defaultValue: string, action: (val: string) => Promise<void>) => {
    setModalTitle(title);
    setModalValue(defaultValue);
    modalActionRef.current = action;
    setModalVisible(true);
  };

  const handleModalOk = async () => {
    if (!modalActionRef.current || !modalValue.trim()) return;
    setModalLoading(true);
    try {
      await modalActionRef.current(modalValue.trim());
      setModalVisible(false);
      message.success('操作成功');
    } catch (e: any) {
      message.error(e?.message || '操作失败');
    } finally {
      setModalLoading(false);
    }
  };

  const handleRefresh = () => {
    refresh().catch((e: any) => message.error(e?.message || '刷新失败'));
  };

  const getParentPath = () => selectedNode?.data?.type === 'folder' ? selectedNode.data.path : '';

  const handleAddFile = () => {
    const parentPath = getParentPath();
    openInputModal('新建文件', '', (val) => createFileNode(parentPath, val));
  };

  const handleAddFolder = () => {
    const parentPath = getParentPath();
    openInputModal('新建文件夹', '', (val) => createFolderNode(parentPath, val));
  };

  return (
    <div className="lt-filetree">
      <FileTreeHeader
        view={view}
        viewOptions={buildViewOptions()}
        onViewChange={switchTreeView}
        onRefresh={handleRefresh}
        onAddFile={handleAddFile}
        onAddFolder={handleAddFolder}
        onCollapseAll={collapseAll}
        onCollapsePanel={() => setLeftTop(null)}
      />
      <div className="lt-filetree-divider" />
      <FileTreeSearch />
      <FileTreeContent openInputModal={openInputModal} />
      <Modal
        open={modalVisible}
        title={modalTitle}
        onOk={handleModalOk}
        onCancel={() => setModalVisible(false)}
        confirmLoading={modalLoading}
      >
        <Input
          value={modalValue}
          onChange={(e) => setModalValue(e.target.value)}
          onPressEnter={handleModalOk}
          autoFocus
          placeholder="请输入名称"
        />
      </Modal>
    </div>
  );
};

export default FileTree;
