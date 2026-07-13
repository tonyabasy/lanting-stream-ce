import { useCallback, useState } from 'react';
import type { FileTreeNode } from '@/pages/editor/types/file';
import { tree } from '@/services/fileTree';

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

  const [selectedNode, setSelectedNode] = useState<FileTreeNode | null>(null);
  const [loading, setLoading] = useState(false);

  const loadTree = useCallback(async (parentPath: string) => {
    setLoading(true);
    try {
      const nodes = await tree(parentPath);
      if (parentPath === '') {
        setTreeData(nodes);
      } else {
        setTreeData((origin) => updateTreeData(origin, parentPath, nodes));
      }
    } finally {
      setLoading(false);
    }
  }, []);

  const selectNode = useCallback((node: FileTreeNode | null) => {
    setSelectedNode(node);
  }, []);

  const toggleExpand = useCallback((key: string, expanded: boolean) => {
    setExpandedKeys((prev) =>
      expanded ? [...prev, key] : prev.filter((k) => k !== key),
    );
  }, []);

  return {
    treeData,
    expandedKeys,
    selectedNode,
    loading,
    loadTree,
    selectNode,
    toggleExpand,
  };
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
