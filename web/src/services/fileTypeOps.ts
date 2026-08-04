import { deleteFile, createFile as createFileApi, saveContent } from './fileTree';
import { deleteTable, saveTable, createTable } from './table';

/**
 * 某一文件类型对应的一组操作。
 *
 * 默认所有类型都可删除；save / create 缺省时回退到普通文件操作。
 */
interface FileTypeOps {
  /** 删除（必填，所有类型都可删） */
  delete: (fileId: number) => Promise<void>;
  /** 保存内容（缺省回退文件保存） */
  save?: (fileId: number, content: string) => Promise<void>;
  /** 创建，返回创建结果（缺省回退文件创建） */
  create?: (path: string) => Promise<{ fileId: number; path: string }>;
}

/** 默认：普通文件走 FileController */
const DEFAULT_OPS: FileTypeOps = {
  delete: (fileId) => deleteFile(fileId),
  save: (fileId, content) => saveContent(fileId, content),
  create: (path) => createFileApi(path),
};

/**
 * 注册表：扩展名 → 专属操作集。
 *
 * 加新文件类型只需在此加一个注册项，调用点无需改动。
 */
const OPS_BY_TYPE: Record<string, FileTypeOps> = {
  ddl: {
    delete: (fileId) => deleteTable(fileId),
    save: (fileId, content) => saveTable(fileId, content),
    create: (path) => createTable(path),
  },
  // 未来：sql: { ...SqlFileOps }, md: { ... } 等
};

/** 按文件类型取操作集；未知/空类型回退默认文件操作 */
export const opsOf = (fileType?: string): FileTypeOps =>
  (fileType ? OPS_BY_TYPE[fileType] : undefined) ?? DEFAULT_OPS;
