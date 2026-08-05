import { useRef, useState } from 'react';
import { Button, Empty, Segmented } from 'antd';
import { IconBraces, IconFirewallCheck, IconDeviceFloppy, IconCodeblock, IconTable } from '@tabler/icons-react';
import { useModel } from 'umi';
import Toolbar from '@/components/EditorToolbar';
import TablerIcon from '@/components/TablerIcon';
import Tooltip from '@/components/Tooltip';
import CodeEditor, { type CodeEditorRef } from '@/pages/dev/components/CodeEditor';
import './index.css';

export interface TableEditorProps {
  fileId: number;
  /** 只读状态（EditorPanel 从锁状态计算后传入） */
  readonly: boolean;
  /** 文件内容（Disk 上的 .ddl 文本） */
  content: string;
}

/** 模态 */
type Mode = 'form' | 'text';

/**
 * DDL 表双模态编辑器（表单 / 文本）。
 *
 * 数据流（序列化/反序列化均由后端承担）：
 * - 打开/文本→表单：content → deserialize → FlinkTableVO → TableFormData
 * - 保存/表单→文本：TableFormData → FlinkTableVO → serialize → DDL 文本 → autoSave 写盘
 */
const TableEditor: React.FC<TableEditorProps> = ({ fileId, readonly, content }) => {
  const { autoSave, baselineDocs, setDirtyFlags, checkClean, dirtyFlags } = useModel('editor');
  const codeEditorRef = useRef<CodeEditorRef>(null);
  const [mode, setMode] = useState<Mode>('text');


  return (
    <>
      {/* 操作栏：左=动作按钮（常显），右=模态切换 */}
      <Toolbar
        className="lt-table-editor-toolbar"
        left={
          <>
            <Tooltip title="验证">
              <Button type="text" color='primary' icon={<TablerIcon icon={IconFirewallCheck} />} />
            </Tooltip>
            <Tooltip title="格式化">
              <Button type="text" color='primary' icon={<TablerIcon icon={IconBraces} />} />
            </Tooltip>
            <Tooltip title="保存">
              <Button type="text" color='primary' icon={<TablerIcon icon={IconDeviceFloppy} />} onClick={() => {}} />
            </Tooltip>
          </>
        }
        right={
          <Segmented
            value={mode}
            onChange={(v) => setMode(v as Mode)}
            options={[
              { icon: <TablerIcon icon={IconTable} />, value: 'form' },
              { icon: <TablerIcon icon={IconCodeblock} />, value: 'text' },
            ]}
          />
        }
      />

      {/* 内容区 */}
      <div className="lt-table-editor-body">
        {mode === 'form' ? (
          <Empty>开发中</Empty>
        ) : (
          <CodeEditor
            ref={codeEditorRef}
            activeTabId={fileId}
            readonly={readonly}
            baselineDocs={baselineDocs}
            setDirtyFlags={setDirtyFlags}
            checkClean={checkClean}
            autoSave={autoSave}
          />
        )}
      </div>
    </>
  );
};

export default TableEditor;
