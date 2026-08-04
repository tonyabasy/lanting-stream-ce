import type { TreeDataNode } from 'antd';

/**
 * 文件树节点类型，与后端 FileTreeNode VO 对齐。
 *
 * 后端接口：GET /api/files/tree?parentPath=&sort=name
 */
export interface FileTreeNode {
  /** 文件/文件夹 ID */
  fileId: number;
  /** 文件/文件夹名 */
  name: string;
  /** 相对路径 */
  path: string;
  /** 类型：file / folder */
  type: 'file' | 'folder';
  /** 文件类型 = 扩展名（小写不含点），前端按文件名推断（'ddl' 路由到 TableController）；目录为 undefined */
  fileType?: string;
  /** 当前持锁人 username，null 表示未被锁定 */
  lockedBy?: string | null;
  /** 抢锁时间戳（毫秒） */
  lockedAt?: number | null;
  /** 文件最后修改时间（毫秒） */
  mtime?: number;
  /** 子节点，folder 时有值，file 时为 undefined */
  children?: FileTreeNode[];
}

/**
 * 前端渲染用树节点，兼容 antd TreeDataNode，可直接作为 Tree treeData。
 *
 * 相比 FileTreeNode 额外携带：
 * - key：节点路径（同 FileTreeNode.path）
 * - data：原始后端节点，业务操作（删除/重命名/移动/打开）需要 path/type/lockedBy 时取回
 * - isVirtual：虚拟目录标记（如 TableTypeView 按连接器分组的虚拟目录），无 fileId，操作路由跳过
 */
export interface FileTreeDataNode extends TreeDataNode {
  /** 文件/文件夹 ID（虚拟目录为 undefined） */
  fileId?: number;
  /** 是否自己持有锁 */
  isMyLock?: boolean;
  /** 原始后端节点 */
  data?: FileTreeNode;
  /** 虚拟目录标记（虚拟目录不是磁盘实体，无 fileId，操作路由跳过） */
  isVirtual?: boolean;
}

/**
 * 文件树视图属性。
 *
 * 对应 Java: interface FileTreeViewProp + Map<String, FileTreeViewProp>
 */
export interface FileTreeViewProp {
  /** 视图 key（唯一标识，状态存这个） */
  key: string;
  /** 数据源根路径 */
  rootPath: string;
  /** Header 标题 */
  title: string;
}

/** 视图 key 联合类型：'workspace' | 'project' | 'tables' */
export type FileTreeViewKey = 'workspace' | 'project' | 'tables';
