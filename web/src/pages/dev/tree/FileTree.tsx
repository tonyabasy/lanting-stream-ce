import { useState, useRef } from 'react';
import { Input, Modal, message } from 'antd';
import { useModel } from 'umi';
import FileTreeHeader from './FileTreeHeader';
import FileTreeSearch from './FileTreeSearch';
import FileTreeContent from './FileTreeContent';
import '../index.css';

/**
 * 文件树组件。
 *
 * 状态由全局 fileTree model 管理。
 */
const FileTree: React.FC = () => {
  const { refresh, collapseAll, createFileNode, createFolderNode, selectedNode } = useModel('fileTree');
  const { toggleLeftTop } = useModel('devPanels');

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

  const getParentPath = () => selectedNode?.type === 'folder' ? selectedNode.path : '';

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
        onRefresh={handleRefresh}
        onAddFile={handleAddFile}
        onAddFolder={handleAddFolder}
        onCollapseAll={collapseAll}
        onCollapsePanel={() => toggleLeftTop('files')}
      />
      <FileTreeSearch />
      <div className="lt-filetree-divider" />
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
