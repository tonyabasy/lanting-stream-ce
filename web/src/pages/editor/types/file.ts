/**
 * 文件树节点类型，与后端 FileTreeNode VO 对齐。
 *
 * 后端接口：GET /api/files/tree?parentPath=&sort=name
 */
export interface FileTreeNode {
  /** 文件/文件夹名 */
  name: string;
  /** 相对路径 */
  path: string;
  /** 类型：file / folder */
  type: 'file' | 'folder';
  /** 当前持锁人 username，null 表示未被锁定 */
  lockedBy?: string | null;
  /** 抢锁时间戳（毫秒） */
  lockedAt?: number | null;
  /** 文件最后修改时间（毫秒） */
  mtime?: number;
  /** 子节点，folder 时有值，file 时为 undefined */
  children?: FileTreeNode[];
}
