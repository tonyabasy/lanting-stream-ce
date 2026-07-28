import request from '@/utils/request';
import type { FileTreeNode } from '@/pages/dev/types/file';

/**
 * 获取文件树。
 *
 * 本地开发时由 web/mock/fileTree.ts 拦截并返回 mock 数据；
 * 生产环境请求会打到真实后端服务。
 */
export const tree = (parentPath: string = ''): Promise<FileTreeNode[]> =>
  request.get('/files/tree', { params: { parentPath } });

/** 搜索文件（按文件名模糊匹配） */
export const searchFiles = (q: string): Promise<FileTreeNode[]> =>
  request.get('/files/search', { params: { q } });

/** 抢锁 */
export const acquireLock = (fileId: number): Promise<void> =>
  request.post('/files/lock/acquire', { fileId });

/** 释放锁 */
export const releaseLock = (fileId: number): Promise<void> =>
  request.post('/files/lock/release', { fileId });

/** 重命名文件或文件夹 */
export const renameFile = (fileId: number, newName: string): Promise<{ fileId: number; oldPath: string; newPath: string }> =>
  request.post('/files/rename', { fileId, newName });

/** 删除文件或文件夹（软删除） */
export const deleteFile = (fileId: number): Promise<void> =>
  request.delete('/files', { params: { fileId } });

/** 创建空文件 */
export const createFile = (path: string): Promise<{ fileId: number; path: string }> =>
  request.post('/files/create', { path });

/** 创建文件夹 */
export const createFolder = (path: string): Promise<{ fileId: number; path: string }> =>
  request.post('/files/folder', { path });

/** 移动文件或文件夹 */
export const moveFile = (fileId: number, newPath: string): Promise<{ fileId: number; oldPath: string; newPath: string }> =>
  request.post('/files/move', { fileId, newPath });

/** 读取文件内容 */
export const loadContent = (fileId: number): Promise<string> =>
  request.get('/files/content', { params: { fileId } });

/** 保存文件内容 */
export const saveContent = (fileId: number, content: string): Promise<void> =>
  request.post('/files/save', { fileId, content });
