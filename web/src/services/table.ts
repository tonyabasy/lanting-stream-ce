import request from '@/utils/request';
import type { FlinkTableVO, TableVO } from '@/types/table';

/** 反序列化：DDL 文本 → 结构化表单数据（纯解析不落库） */
export const stringToCreateTable = (ddl: string): Promise<FlinkTableVO> =>
  request.post('/tables/utils/deserialize', { ddl });

/** 序列化：表单数据 → DDL 文本（纯生成不落库） */
export const createTableToString = (form: FlinkTableVO): Promise<string> =>
  request.post('/tables/utils/serialize', form);

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
