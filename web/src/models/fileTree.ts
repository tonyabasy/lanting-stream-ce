import { useCallback, useRef, useState } from 'react';
import type { FileTreeNode } from '@/pages/dev/types/file';
import {
  tree,
  searchFiles,
  acquireLock as acquireLockApi,
  releaseLock as releaseLockApi,
  renameFile,
  deleteFile,
  createFile as createFileApi,
  createFolder as createFolderApi,
  moveFile,
} from '@/services/fileTree';

/**
 * 全局文件树状态 model。
 *
 * 任何组件都可以通过 useModel('fileTree') 读取或操作目录状态。
 */
export default () => {
  const [treeData, setTreeData] = useState<FileTreeNode[]>([]);

  /**
   * 当前展开的目录节点 key 列表。
   *
   * key 对应文件/目录的相对路径，例如：
   * - 'docs' 表示 docs 目录展开
   * - 'sql/ods' 表示 sql/ods 目录展开
   *
   * 示例：
   * expandedKeys = []                          // 全部收起
   * expandedKeys = ['docs']                    // 展开 docs
   * expandedKeys = ['docs', 'sql', 'sql/ods']  // 展开 docs、sql、sql/ods
   */
  const [expandedKeys, setExpandedKeys] = useState<string[]>([]);
  const [loadedKeys, setLoadedKeys] = useState<string[]>([]);

  const [selectedNode, setSelectedNode] = useState<FileTreeNode | null>(null);
  const [loading, setLoading] = useState(false);

  // ==================== 搜索 ====================

  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState<FileTreeNode[]>([]);
  const [searchLoading, setSearchLoading] = useState(false);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const isSearching = searchQuery.trim() !== '';

  const doSearch = useCallback(async (q: string) => {
    if (!q.trim()) {
      setSearchResults([]);
      return;
    }
    setSearchLoading(true);
    try {
      const results = await searchFiles(q.trim());
      setSearchResults(results);
    } finally {
      setSearchLoading(false);
    }
  }, []);

  const handleSearchChange = useCallback((q: string) => {
    setSearchQuery(q);
    if (debounceRef.current) clearTimeout(debounceRef.current);
    if (!q.trim()) {
      setSearchResults([]);
      return;
    }
    debounceRef.current = setTimeout(() => doSearch(q), 300);
  }, [doSearch]);

  const clearSearch = useCallback(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current);
    setSearchQuery('');
    setSearchResults([]);
  }, []);

  // ==================== 树操作 ====================

  const loadTree = useCallback(async (parentPath: string) => {
    setLoading(true);
    try {
      const nodes = await tree(parentPath);
      if (parentPath === '') {
        setTreeData(nodes);
      } else {
        setTreeData((origin) => updateTreeData(origin, parentPath, nodes));
        setLoadedKeys((prev) => prev.includes(parentPath) ? prev : [...prev, parentPath]);
      }
    } finally {
      setLoading(false);
    }
  }, []);

  /**
   * 将树展开到目标路径，确保所有祖先目录已加载并展开。
   *
   * 'sql/ods/foo.sql' → 依次加载并展开 'sql'、'sql/ods'，
   * 最后选中目标文件。
   */
  const expandToPath = useCallback(async (path: string) => {
    const segments = path.split('/');
    for (let i = 1; i < segments.length; i++) {
      const ancestor = segments.slice(0, i).join('/');
      await loadTree(ancestor);
      setExpandedKeys((prev) => prev.includes(ancestor) ? prev : [...prev, ancestor]);
    }
  }, [loadTree]);

  const selectNode = useCallback((node: FileTreeNode | null) => {
    setSelectedNode(node);
  }, []);

  const toggleExpand = useCallback((key: string, expanded: boolean) => {
    setExpandedKeys((prev) =>
      expanded ? [...prev, key] : prev.filter((k) => k !== key),
    );
  }, []);

  /** 收起全部展开的节点 */
  const collapseAll = useCallback(() => {
    setExpandedKeys([]);
  }, []);

  /** 刷新根目录数据，保留当前展开状态 */
  const refresh = useCallback(async () => {
    await loadTree('');
    setLoadedKeys([]);
  }, [loadTree]);

  /** 抢锁，成功后刷新所在目录 */
  const acquireLock = useCallback(async (fileId: number, path: string) => {
    await acquireLockApi(fileId);
    const parentPath = parentOf(path);
    await loadTree(parentPath);
  }, [loadTree]);

  /** 释放锁，成功后刷新所在目录 */
  const releaseLock = useCallback(async (fileId: number, path: string) => {
    await releaseLockApi(fileId);
    const parentPath = parentOf(path);
    await loadTree(parentPath);
  }, [loadTree]);

  /** 重命名，成功后刷新所在目录 */
  const renameNode = useCallback(async (fileId: number, path: string, newName: string) => {
    await renameFile(fileId, newName);
    const parentPath = parentOf(path);
    await loadTree(parentPath);
  }, [loadTree]);

  /** 删除，成功后清除选中（如被删）并刷新所在目录 */
  const deleteNode = useCallback(async (fileId: number, path: string) => {
    await deleteFile(fileId);
    setSelectedNode((prev) => prev?.fileId === fileId ? null : prev);
    const parentPath = parentOf(path);
    await loadTree(parentPath);
  }, [loadTree]);

  /** 新建文件 */
  const createFileNode = useCallback(async (parentPath: string, name: string) => {
    const fullPath = parentPath ? `${parentPath}/${name}` : name;
    await createFileApi(fullPath);
    await loadTree(parentPath);
  }, [loadTree]);

  /** 新建文件夹 */
  const createFolderNode = useCallback(async (parentPath: string, name: string) => {
    const fullPath = parentPath ? `${parentPath}/${name}` : name;
    await createFolderApi(fullPath);
    await loadTree(parentPath);
  }, [loadTree]);

  /** 移动文件或文件夹，刷新新旧两个父目录 */
  const moveNode = useCallback(async (fileId: number, oldPath: string, newPath: string) => {
    await moveFile(fileId, newPath);
    const oldParent = parentOf(oldPath);
    const newParent = parentOf(newPath);
    await loadTree(oldParent);
    if (newParent !== oldParent) {
      await loadTree(newParent);
    }
  }, [loadTree]);

  return {
    treeData,
    expandedKeys,
    loadedKeys,
    selectedNode,
    loading,
    // 搜索
    searchQuery,
    setSearchQuery: handleSearchChange,
    searchResults,
    searchLoading,
    isSearching,
    clearSearch,
    expandToPath,
    // 树操作
    loadTree,
    refresh,
    selectNode,
    toggleExpand,
    collapseAll,
    acquireLock,
    releaseLock,
    renameNode,
    deleteNode,
    createFileNode,
    createFolderNode,
    moveNode,
  };
};

/**
 * 从完整路径提取父目录路径。
 * 'README.md' → ''，'sql/ods/foo.sql' → 'sql/ods'
 */
const parentOf = (path: string): string => {
  const i = path.lastIndexOf('/');
  return i > 0 ? path.substring(0, i) : '';
};

/**
 * 更新指定 path 的 children，返回新的 treeData。
 */
const updateTreeData = (
  list: FileTreeNode[],
  key: string,
  children: FileTreeNode[],
): FileTreeNode[] =>
  list.map((node) => {
    if (node.path === key) {
      return { ...node, children };
    }
    if (node.children) {
      return { ...node, children: updateTreeData(node.children, key, children) };
    }
    return node;
  });
