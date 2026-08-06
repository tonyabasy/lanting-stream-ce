import { useCallback, useRef, useState } from 'react';
import { FileTreeDataNode, FileTreeViewKey, FileTreeViewProp } from '@/types/file';
import { toTreeDataNode, parentOf } from '@/pages/dev/components/FileTree/treeUtils';
import {
  tree,
  searchFiles,
  renameFile,
  createFile as createFileApi,
  createFolder as createFolderApi,
  moveFile,
} from '@/services/fileTree';
import { opsOf } from '@/services/fileTypeOps';

/** 视图配置表：key → 视图属性（对应 Java 的 Map<String, FileTreeViewProp>） */
export const FileTreeViews: Record<string, FileTreeViewProp> = {
  workspace: { key: 'workspace', rootPath: '', title: 'Workspace' },
  project:   { key: 'project',   rootPath: 'project', title: 'Project' },
  tables:    { key: 'tables',    rootPath: 'tables', title: 'Tables' },
};

/**
 * 全局文件树状态 model。
 *
 * 任何组件都可以通过 useModel('fileTree') 读取或操作目录状态。
 */
export default () => {
  /**
   * 当前视图根路径。
   * 视图切换 = 调用 switchTreeView 重新拉取并替换 treeData。
   * - Project 视图：''
   * - Table 视图：'tables/'
   */
  const [rootPath, setRootPath] = useState<string>('');
  const [viewKey, setViewKey] = useState<FileTreeViewKey>('workspace');
  const [treeData, setTreeData] = useState<FileTreeDataNode[]>([]);

  /**
   * 当前展开的目录节点 key 列表。
   *
   * key 对应文件/目录的相对路径，例如：
   * - 'docs' 表示 docs 目录展开
   * - 'project/ods' 表示 project/ods 目录展开
   *
   * 示例：
   * expandedKeys = []                          // 全部收起
   * expandedKeys = ['docs']                    // 展开 docs
   * expandedKeys = ['docs', 'project', 'project/ods']  // 展开 docs、project、project/ods
   */
  const [expandedKeys, setExpandedKeys] = useState<string[]>([]);
  const [loadedKeys, setLoadedKeys] = useState<string[]>([]);

  const [selectedNode, setSelectedNode] = useState<FileTreeDataNode | null>(null);
  const [loading, setLoading] = useState(false);

  // ==================== 搜索 ====================

  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState<FileTreeDataNode[]>([]);
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
      setSearchResults(results.map((node) => toTreeDataNode(node)));
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

  /**
   * 切换视图：查表取 rootPath，重新拉取根数据，替换整棵 treeData。
   * 视图 key 作为当前视图状态保存；加载期间 loading=true（Content 禁用交互），
   * 数据回来才解锁，避免竞态。
   */
  const switchTreeView = useCallback(async (key: FileTreeViewKey) => {
    const view = FileTreeViews[key];
    if (!view) return;
    setViewKey(key);
    setRootPath(view.rootPath);
    setLoading(true);
    try {
      clearSearch()
      await refresh(view.rootPath);
    } finally {
      setLoading(false);
    }
  }, []);

  const loadTree = useCallback(async (parentPath: string, replaceRoot = false) => {
    setLoading(true);
    try {
      const nodes = await tree(parentPath);
      const antdNodes = nodes.map((node) => toTreeDataNode(node));
      if (replaceRoot || parentPath === '') {
        setTreeData(antdNodes);
      } else {
        setTreeData((origin) => updateTreeData(origin, parentPath, antdNodes));
        setLoadedKeys((prev) => prev.includes(parentPath) ? prev : [...prev, parentPath]);
      }
    } finally {
      setLoading(false);
    }
  }, []);

  /**
   * 将树展开到目标路径，确保所有祖先目录已加载并展开。
   *
   * 'project/ods/foo.sql' → 依次加载并展开 'project'、'project/ods'，
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

  const selectNode = useCallback((node: FileTreeDataNode | null) => {
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

  /** 刷新当前视图根目录数据，保留当前展开状态 */
  const refresh = useCallback(async (path?: string) => {
    await loadTree(path ?? rootPath, true);
    setLoadedKeys([]);
  }, [rootPath]);


  /** 重命名，成功后刷新所在目录 */
  const renameNode = useCallback(async (fileId: number, path: string, newName: string) => {
    await renameFile(fileId, newName);
    const parentPath = parentOf(path);
    await loadTree(parentPath);
  }, [loadTree]);

  /** 删除：按文件类型路由到对应操作集，成功后清除选中（如被删）并刷新所在目录 */
  const deleteNode = useCallback(async (node: FileTreeDataNode) => {
    const raw = node.data;
    if (!raw) return;
    await opsOf(raw.fileType).delete(raw.fileId);
    setSelectedNode((prev) => prev?.fileId === raw.fileId ? null : prev);
    const parentPath = parentOf(raw.path);
    await loadTree(parentPath);
  }, [loadTree]);

  /** 新建文件：按名字后缀路由到对应操作集 */
  const createFileNode = useCallback(async (parentPath: string, name: string) => {
    const fullPath = parentPath ? `${parentPath}/${name}` : name;
    const dot = name.lastIndexOf('.');
    const ext = dot >= 0 && dot < name.length - 1 ? name.substring(dot + 1).toLowerCase() : undefined;
    await (opsOf(ext).create?.(fullPath) ?? createFileApi(fullPath));
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
    rootPath,
    viewKey,
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
    switchTreeView,
    loadTree,
    refresh,
    selectNode,
    toggleExpand,
    collapseAll,
    renameNode,
    deleteNode,
    createFileNode,
    createFolderNode,
    moveNode,
  };
};

/**
 * 更新指定 key（路径）的 children，返回新的 treeData。
 */
const updateTreeData = (
  list: FileTreeDataNode[],
  key: string,
  children: FileTreeDataNode[],
): FileTreeDataNode[] =>
  list.map((node) => {
    if (node.key === key) {
      return { ...node, children };
    }
    if (node.children) {
      return { ...node, children: updateTreeData(node.children as FileTreeDataNode[], key, children) };
    }
    return node;
  });
