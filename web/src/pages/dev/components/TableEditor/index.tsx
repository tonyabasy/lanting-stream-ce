import { Empty } from 'antd';

export interface TableEditorProps {
  fileId: number;
  /** 只读状态（EditorPanel 从锁状态计算后传入） */
  readonly: boolean;
  /** 文件内容（Disk 上的 .ddl 文本） */
  content: string;
}

/**
 * DDL 表双模态编辑器（表单 / 文本）。
 *
 * CP1 占位：仅验证路由链路，CP4 组装 Segmented 切换 + 双向同步。
 */
const TableEditor: React.FC<TableEditorProps> = () => (
  <div className="lt-table-editor">
    <Empty description="Table 编辑器开发中（CP1 占位）" />
  </div>
);

export default TableEditor;
