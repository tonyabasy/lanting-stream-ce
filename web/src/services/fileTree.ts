import request from '@/utils/request';
import type { FileTreeNode } from '@/pages/editor/types/file';

/**
 * 获取文件树。
 *
 * 本地开发时由 web/mock/fileTree.ts 拦截并返回 mock 数据；
 * 生产环境请求会打到真实后端服务。
 */
export const tree = (parentPath: string = ''): Promise<FileTreeNode[]> =>
  request.get('/files/tree', { params: { parentPath } });
