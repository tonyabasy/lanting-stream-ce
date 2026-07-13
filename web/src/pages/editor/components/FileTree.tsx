import { useEffect } from 'react';
import { Tree, Button, Tooltip, Input } from 'antd';
import type { DataNode, TreeProps } from 'antd/es/tree';
import { useModel } from 'umi';
import {
  IconFolder,
  IconFile,
  IconMarkdown,
  IconSql,
  IconDatabase,
  IconJson,
  IconHtml,
  IconPlus,
  IconFocusCentered,
  IconArrowsMinimize,
  IconLayoutSidebarLeftCollapse,
  IconSearch,
  IconChevronDown,
  IconLock,
} from '@tabler/icons-react';
import type { FileTreeNode } from '../types/file';
import '../index.css';

/* 设计稿规范为 11px，奇数尺寸 SVG 渲染发虚，取 12px */
const TREE_ICON_SIZE = 12;

const getFileIcon = (name: string) => {
  if (name.endsWith('.md')) return <IconMarkdown size={TREE_ICON_SIZE} />;
  if (name.endsWith('.sql')) return <IconSql size={TREE_ICON_SIZE} />;
  if (name.endsWith('.ddl')) return <IconDatabase size={TREE_ICON_SIZE} />;
  if (name.endsWith('.json')) return <IconJson size={TREE_ICON_SIZE} />;
  if (name.endsWith('.html')) return <IconHtml size={TREE_ICON_SIZE} />;
  return <IconFile size={TREE_ICON_SIZE} />;
};

const toTreeDataNode = (node: FileTreeNode, currentUsername?: string): DataNode => {
  const isFolder = node.type === 'folder';
  // 锁只在当前用户持有时展示（R3：不展示他人持锁，避免信息噪音）
  const holdsLock = !!node.lockedBy && node.lockedBy === currentUsername;
  return {
    key: node.path,
    title: holdsLock ? (
      <span className="lt-filetree-node-title">
        {node.name}
        <IconLock size={TREE_ICON_SIZE} className="lt-filetree-lock" />
      </span>
    ) : (
      node.name
    ),
    icon: isFolder ? <IconFolder size={TREE_ICON_SIZE} /> : getFileIcon(node.name),
    isLeaf: !isFolder,
    children: node.children?.map((child) => toTreeDataNode(child, currentUsername)),
  };
};

/**
 * 文件树组件。
 *
 * 状态由全局 fileTree model 管理，本组件只负责渲染和事件转发。
 */
const FileTree: React.FC = () => (
  <div className="lt-filetree">
    <FileTreeHeader />
    <FileTreeSearch />
    <div className="lt-filetree-divider" />
    <FileTreeContent />
  </div>
);

/**
 * 顶部操作区：标题 + 高频操作按钮。
 */
const FileTreeHeader: React.FC = () => (
  <div className="lt-filetree-header">
    <span className="lt-filetree-title">SQL 文件</span>
    <Tooltip title="新增">
      <Button type="text" className="lt-filetree-hbtn" icon={<IconPlus size={13} />} />
    </Tooltip>
    <Tooltip title="定位">
      <Button type="text" className="lt-filetree-hbtn" icon={<IconFocusCentered size={13} />} />
    </Tooltip>
    <Tooltip title="收起全部">
      <Button type="text" className="lt-filetree-hbtn" icon={<IconArrowsMinimize size={13} />} />
    </Tooltip>
    <Tooltip title="收起面板">
      <Button type="text" className="lt-filetree-hbtn" icon={<IconLayoutSidebarLeftCollapse size={13} />} />
    </Tooltip>
  </div>
);

/**
 * 搜索框。
 */
const FileTreeSearch: React.FC = () => (
  <div className="lt-filetree-search">
    <Input
      size="small"
      placeholder="搜索文件名，或 @me @today @20250607"
      prefix={<IconSearch size={12} />}
    />
  </div>
);

/**
 * 树内容区：文件/目录列表。
 */
const FileTreeContent: React.FC = () => {
  const {
    treeData,
    expandedKeys,
    selectedNode,
    loadTree,
    selectNode,
    toggleExpand,
  } = useModel('fileTree');
  const { initialState } = useModel('@@initialState');
  const currentUsername = initialState?.currentUser?.username;

  useEffect(() => {
    loadTree('');
  }, [loadTree]);

  const selectedKeys = selectedNode ? [selectedNode.path] : [];

  const onLoadData: TreeProps['loadData'] = ({ key, children }) =>
    new Promise<void>((resolve) => {
      if (children && (children as DataNode[]).length > 0) {
        resolve();
        return;
      }
      loadTree(String(key)).then(() => resolve());
    });

  const onExpand: TreeProps['onExpand'] = (_keys, { expanded, node }) => {
    toggleExpand(String(node.key), expanded);
  };

  const onSelect: TreeProps['onSelect'] = (_keys, { node }) => {
    const path = String(node.key);
    const found = findNode(treeData, path);
    selectNode(found ?? null);
  };

  return (
    <div className="lt-filetree-body">
      <Tree
        treeData={treeData.map((node) => toTreeDataNode(node, currentUsername))}
        loadData={onLoadData}
        showIcon
        switcherIcon={<IconChevronDown size={TREE_ICON_SIZE} className="lt-filetree-chevron" />}
        expandedKeys={expandedKeys}
        selectedKeys={selectedKeys}
        onExpand={onExpand}
        onSelect={onSelect}
      />
    </div>
  );
};

/**
 * 根据 path 在 treeData 中查找节点。
 */
const findNode = (list: FileTreeNode[], path: string): FileTreeNode | undefined => {
  for (const node of list) {
    if (node.path === path) return node;
    if (node.children) {
      const found = findNode(node.children, path);
      if (found) return found;
    }
  }
  return undefined;
};

export default FileTree;
