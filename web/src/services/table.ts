import request from '@/utils/request';
import type { TableVO } from '@/types/table';

/** 创建表：后端创建 .ddl 文件（ddl 可选，不传则建空文件）并解析索引 */
export const createTable = (path: string, ddl?: string): Promise<{ fileId: number; tableId: number; path: string }> =>
  request.post('/tables', { path, ddl });

/** 保存表 DDL：按 fileId 写文件并同步索引 */
export const saveTable = (fileId: number, ddl: string): Promise<void> =>
  request.put(`/tables/${fileId}`, { ddl });

/** 删除表：按 fileId 删除 .ddl 文件并级联删索引 */
export const deleteTable = (fileId: number): Promise<void> =>
  request.delete(`/tables/${fileId}`);

/** 查询全部表 */
export const listTables = (): Promise<TableVO[]> =>
  request.get('/tables');

/** 查询表详情（按 fileId） */
export const getTable = (fileId: number): Promise<TableVO> =>
  request.get(`/tables/${fileId}`);

/** 搜索表（按表名/连接器类型） */
export const searchTables = (q: string): Promise<TableVO[]> =>
  request.get('/tables/search', { params: { q } });
