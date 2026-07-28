import {
  IconFolder,
  IconFile,
  IconMarkdown,
  IconSql,
  IconDatabase,
  IconJson,
  IconHtml,
} from '@tabler/icons-react';
import type { TreeDataNode } from 'antd';
import type { ReactNode } from 'react';
import type { FileTreeNode } from '../types/file';

/* 设计稿规范为 11px，奇数尺寸 SVG 渲染发虚，取 12px */
export const TREE_ICON_SIZE = 16;
export const CTX_ICON_SIZE = 16;

export const getFileIcon = (name: string) => {
  if (name.endsWith('.md')) return <IconMarkdown size={TREE_ICON_SIZE} />;
  if (name.endsWith('.sql')) return <IconSql size={TREE_ICON_SIZE} />;
  if (name.endsWith('.ddl')) return <IconDatabase size={TREE_ICON_SIZE} />;
  if (name.endsWith('.json')) return <IconJson size={TREE_ICON_SIZE} />;
  if (name.endsWith('.html')) return <IconHtml size={TREE_ICON_SIZE} />;
  return <IconFile size={TREE_ICON_SIZE} />;
};

/** 将 FileTreeNode 转换为 TreeDataNode */
export const toTreeDataNode = (
  node: FileTreeNode,
): TreeDataNode => {
  const isFolder = node.type === 'folder';
  return {
    key: node.path,
    fileId: node.fileId,
    title: node.name,
    icon: isFolder ? <IconFolder size={TREE_ICON_SIZE} /> : getFileIcon(node.name),
    isLeaf: !isFolder,
    children: node.children?.map((child) => toTreeDataNode(child)),
  } as TreeDataNode & { fileId: number; isMyLock: boolean };
};

/** 从路径提取末段名称。'README.md' → 'README.md' */
export const leafName = (path: string): string => {
  const i = path.lastIndexOf('/');
  return i >= 0 ? path.substring(i + 1) : path;
};

/** 从路径提取父目录。'README.md' → '' */
export const parentOf = (path: string): string => {
  const i = path.lastIndexOf('/');
  return i > 0 ? path.substring(0, i) : '';
};

/** 根据 path 在 treeData 中查找节点 */
export const findNode = (list: FileTreeNode[], path: string): FileTreeNode | undefined => {
  for (const node of list) {
    if (node.path === path) return node;
    if (node.children) {
      const found = findNode(node.children, path);
      if (found) return found;
    }
  }
  return undefined;
};

/** 文件名匹配高亮：将 text 中匹配 keyword 的部分用 <mark> 包裹 */
export const highlightMatch = (text: string, keyword: string): ReactNode => {
  if (!keyword) return text;
  const idx = text.toLowerCase().indexOf(keyword.toLowerCase());
  if (idx === -1) return text;
  return (
    <>
      {text.slice(0, idx)}
      <mark>{text.slice(idx, idx + keyword.length)}</mark>
      {text.slice(idx + keyword.length)}
    </>
  );
};
