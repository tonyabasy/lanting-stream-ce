import {
  IconFolder,
  IconFile,
  IconMarkdown,
  IconSql,
  IconDatabase,
  IconJson,
  IconHtml,
} from '@tabler/icons-react';
import TablerIcon from '@/components/TablerIcon';
import type { ReactNode } from 'react';
import type { FileTreeDataNode, FileTreeNode } from '@/types/file';

/* 设计稿规范为 11px，奇数尺寸 SVG 渲染发虚，取 12px */
export const TREE_ICON_SIZE = 16;
export const TREE_ICON_WEIGHT = 2;
export const CTX_ICON_SIZE = 16;

/** 从文件名提取扩展名（小写，不含点）。目录返回 undefined；无扩展名返回 '' */
export const inferFileType = (name: string, isFolder: boolean): string | undefined => {
  if (isFolder) return undefined;
  const dot = name.lastIndexOf('.');
  return dot >= 0 && dot < name.length - 1 ? name.substring(dot + 1).toLowerCase() : '';
};

export const getFileIcon = (name: string) => {
  if (name.endsWith('.md')) return <TablerIcon icon={IconMarkdown} strokeWidth={TREE_ICON_WEIGHT} size={TREE_ICON_SIZE} />;
  if (name.endsWith('.sql')) return <TablerIcon icon={IconSql} strokeWidth={TREE_ICON_WEIGHT} size={TREE_ICON_SIZE} />;
  if (name.endsWith('.ddl')) return <TablerIcon icon={IconDatabase} strokeWidth={TREE_ICON_WEIGHT} size={TREE_ICON_SIZE} />;
  if (name.endsWith('.json')) return <TablerIcon icon={IconJson} strokeWidth={TREE_ICON_WEIGHT} size={TREE_ICON_SIZE} />;
  if (name.endsWith('.html')) return <TablerIcon icon={IconHtml} strokeWidth={TREE_ICON_WEIGHT} size={TREE_ICON_SIZE} />;
  return <TablerIcon icon={IconFile} strokeWidth={TREE_ICON_WEIGHT} size={TREE_ICON_SIZE} />;
};

/** 将 FileTreeNode 转换为 FileTreeDataNode（antd 节点，携带原始数据引用） */
export const toTreeDataNode = (
  node: FileTreeNode,
): FileTreeDataNode => {
  const isFolder = node.type === 'folder';
  const enriched: FileTreeNode = isFolder
    ? node
    : { ...node, fileType: node.fileType ?? inferFileType(node.name, isFolder) };
  return {
    key: node.path,
    fileId: node.fileId,
    data: enriched,
    title: node.name,
    icon: isFolder ? <TablerIcon icon={IconFolder} size={TREE_ICON_SIZE} /> : getFileIcon(node.name),
    isLeaf: !isFolder,
    children: node.children?.map((child) => toTreeDataNode(child)),
  } as FileTreeDataNode;
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

/** 根据 path 在 treeData 中查找节点（FileTreeDataNode，key 即路径） */
export const findNode = (list: FileTreeDataNode[], path: string): FileTreeDataNode | undefined => {
  for (const node of list) {
    if (node.key === path) return node;
    if (node.children) {
      const found = findNode(node.children as FileTreeDataNode[], path);
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
