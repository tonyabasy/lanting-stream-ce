import { useEffect, useState } from 'react';
import { Tree, Button, Dropdown, Modal, message, Spin, Typography } from 'antd';
import type { TreeDataNode, TreeProps } from 'antd';
import { useModel } from 'umi';
import { IconChevronDown, IconDots, IconFolder } from '@tabler/icons-react';
import { toTreeDataNode, leafName, parentOf, findNode, TREE_ICON_SIZE, getFileIcon, highlightMatch } from './treeUtils';
import { getFileMenuItems, folderMenuItems } from './FileMenuItems';
import type { FileTreeNode } from '../types/file';

const { Text } = Typography;

export interface FileTreeContentProps {
  openInputModal: (title: string, defaultValue: string, action: (val: string) => Promise<void>) => void;
}

const FileTreeContent: React.FC<FileTreeContentProps> = ({ openInputModal }) => {
  const {
    treeData,
    expandedKeys,
    loadedKeys,
    selectedNode,
    searchQuery,
    searchResults,
    searchLoading,
    isSearching,
    loadTree,
    selectNode,
    toggleExpand,
    clearSearch,
    expandToPath,
    acquireLock,
    releaseLock,
    renameNode,
    deleteNode,
    createFileNode,
    createFolderNode,
    moveNode,
  } = useModel('fileTree');
  const { openFile } = useModel('editor');

  useEffect(() => {
    if (treeData.length === 0) {
      loadTree('');
    }
  }, [loadTree, treeData.length]);

  const selectedKeys = selectedNode ? [selectedNode.path] : [];

  // ==================== 搜索交互 ====================

  const [activeSearchIndex, setActiveSearchIndex] = useState(-1);

  const handleSearchResultClick = async (result: FileTreeNode) => {
    await expandToPath(result.path);
    selectNode(result);
    clearSearch();
  };

  const handleSearchKeyDown = (e: React.KeyboardEvent) => {
    if (!searchResults.length) return;
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setActiveSearchIndex((prev) => Math.min(prev + 1, searchResults.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActiveSearchIndex((prev) => Math.max(prev - 1, 0));
    } else if (e.key === 'Enter') {
      if (activeSearchIndex >= 0 && activeSearchIndex < searchResults.length) {
        handleSearchResultClick(searchResults[activeSearchIndex]);
      }
    } else if (e.key === 'Escape') {
      clearSearch();
    }
  };

  // ==================== 树操作 ====================

  const onLoadData: TreeProps['loadData'] = ({ key, children }) =>
    new Promise<void>((resolve) => {
      if (children) {
        resolve();
        return;
      }
      loadTree(String(key)).then(() => resolve());
    });

  const onExpand: TreeProps['onExpand'] = (_keys, { expanded, node }) => {
    toggleExpand(String(node.key), expanded);
  };

  /**
   * 单击选中 TreeNode
   */
  const onSelect: TreeProps['onSelect'] = (_keys, { node }) => {
    const path = String(node.key);
    const found = findNode(treeData, path);
    selectNode(found ?? null);
  };

  /**
   * 双击打开 TreeNode
   */
  const onDoubleClick: TreeProps['onDoubleClick'] = (_event, node) => {
    const path = String(node.key);
    const found = findNode(treeData, path);
    if (!found) return;

    if (found.type === 'file') {
      openFile(found);
    } else {
      const expanded = expandedKeys.includes(path);
      toggleExpand(path, !expanded);
    }
  };

  /** 三点按钮菜单点击 */
  const handleMenuClick = (treeNode: TreeDataNode) => ({ key }: { key: string }) => {
    const node = treeNode as TreeDataNode & { fileId: number };
    const fileId = node.fileId;
    const path = String(node.key);
    const name = leafName(path);
    const isFolder = !node.isLeaf;

    switch (key) {
      case 'lock':
        acquireLock(fileId, path).catch((e: any) => message.error(e?.message || '抢锁失败'));
        break;
      case 'unlock':
        releaseLock(fileId, path).catch((e: any) => message.error(e?.message || '释放锁失败'));
        break;
      case 'rename':
        openInputModal('重命名', name, (val) => renameNode(fileId, path, val));
        break;
      case 'move':
        openInputModal('移动到', path, (val) => moveNode(fileId, path, val));
        break;
      case 'new-file': {
        const parentPath = isFolder ? path : parentOf(path);
        openInputModal('新建文件', '', (val) => createFileNode(parentPath, val));
        break;
      }
      case 'new-folder': {
        const parentPath = isFolder ? path : parentOf(path);
        openInputModal('新建文件夹', '', (val) => createFolderNode(parentPath, val));
        break;
      }
      case 'delete':
        Modal.confirm({
          title: `确定删除「${name}」吗？`,
          content: '删除后可从回收站恢复。',
          okText: '删除',
          okType: 'danger',
          cancelText: '取消',
          onOk: () => deleteNode(fileId, path).catch((e: any) => message.error(e?.message || '删除失败')),
        });
        break;
    }
  };

  const titleRender = (treeNode: TreeDataNode) => {
    const node = treeNode as TreeDataNode & { isMyLock: boolean };

    return (
      <span className="lt-filetree-node-row">
        <span>{treeNode.title as React.ReactNode}</span>
        <Dropdown
          menu={{
            items: treeNode.isLeaf ? getFileMenuItems(node.isMyLock) : folderMenuItems,
            onClick: handleMenuClick(treeNode),
            rootClassName: 'lt-filetree-ctxmenu',
          }}
          trigger={['click']}
        >
          <Button
            type="text"
            size="small"
            className="lt-filetree-more-btn"
            icon={<IconDots size={TREE_ICON_SIZE} />}
            onClick={(e) => e.stopPropagation()}
          />
        </Dropdown>
      </span>
    );
  };

  // ==================== 搜索视图 ====================

  if (isSearching) {
    return (
      <div className="lt-filetree-body" onKeyDown={handleSearchKeyDown}>
        {searchLoading && (
          <div style={{ textAlign: 'center', padding: 16 }}>
            <Spin size="small" />
          </div>
        )}
        {!searchLoading && searchResults.length === 0 && (
          <div style={{ textAlign: 'center', padding: 16, color: 'var(--ant-color-text-description)' }}>
            未找到匹配的文件
          </div>
        )}
        {!searchLoading && searchResults.length > 0 && (
          <div className="lt-filetree-search-results">
            {searchResults.map((result, index) => {
              const isFolder = result.type === 'folder';
              const parent = parentOf(result.path);
              const isActive = index === activeSearchIndex;
              return (
                <div
                  key={result.fileId}
                  className={`lt-filetree-search-item${isActive ? ' lt-filetree-search-item-active' : ''}`}
                  onClick={() => handleSearchResultClick(result)}
                  onMouseEnter={() => setActiveSearchIndex(index)}
                >
                  <span className="lt-filetree-search-item-icon">
                    {isFolder ? <IconFolder size={TREE_ICON_SIZE} /> : getFileIcon(result.name)}
                  </span>
                  <span className="lt-filetree-search-item-name">
                    {highlightMatch(result.name, searchQuery.trim())}
                  </span>
                  {parent && (
                    <Text type="secondary" className="lt-filetree-search-item-path">
                      {parent}/
                    </Text>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>
    );
  }

  // ==================== 树视图 ====================

  return (
    <div className="lt-filetree-body">
      <Tree
        treeData={treeData.map((node) => toTreeDataNode(node))}
        loadData={onLoadData}
        showIcon
        switcherIcon={<IconChevronDown size={TREE_ICON_SIZE} className="lt-filetree-chevron" />}
        expandedKeys={expandedKeys}
        loadedKeys={loadedKeys}
        selectedKeys={selectedKeys}
        onExpand={onExpand}
        onSelect={onSelect}
        onDoubleClick={onDoubleClick}
        titleRender={titleRender}
      />
    </div>
  );
};

export default FileTreeContent;
