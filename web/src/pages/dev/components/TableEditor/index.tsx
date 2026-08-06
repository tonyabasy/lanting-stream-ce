import { useRef, useState } from 'react';
import { Button, message, Segmented } from 'antd';
import { IconBraces, IconFirewallCheck, IconCodeblock, IconLock, IconLockOpen, IconTable } from '@tabler/icons-react';
import { useModel } from 'umi';
import Toolbar from '@/components/EditorToolbar';
import TablerIcon from '@/components/TablerIcon';
import Tooltip from '@/components/Tooltip';
import CodeEditor, { type CodeEditorRef } from '@/pages/dev/components/CodeEditor';
import TableFormEditor from './TableFormEditor';
import './index.css';

export interface TableEditorProps {
  fileId: number;
  /** 只读状态（EditorPanel 从锁状态计算后传入） */
  readonly: boolean;
  /** 文件内容（Disk 上的 .ddl 文本） */
  content: string;
}

/** 编辑模态 */
type Mode = 'form' | 'text';

/**
 * DDL 表双模态编辑器（表单 / 文本）。
 *
 * 数据流（序列化/反序列化均由后端承担）：
 * - 打开/文本→表单：content → deserialize → FlinkTableVO → TableFormData
 * - 保存/表单→文本：TableFormData → FlinkTableVO → serialize → DDL 文本 → saveTable 写盘
 */
const TableEditor: React.FC<TableEditorProps> = ({ fileId, readonly, content }) => {
  const { autoSave, baselineDocs, setDirtyFlags, checkClean, acquireLock, releaseLock, openTabs } = useModel('editor');
  const codeEditorRef = useRef<CodeEditorRef>(null);
  const [mode, setMode] = useState<Mode>('form');

  const activeTab = openTabs.find((t) => t.fileId === fileId);

  const handleLockAction = async () => {
    if (!activeTab) return;
    try {
      if (readonly) {
        await acquireLock(fileId, activeTab.path);
        message.success('抢锁成功，现在可以编辑了');
      } else {
        await releaseLock(fileId, activeTab.path);
        message.success('锁已释放');
      }
    } catch (err: any) {
      message.error(err?.message || '操作失败');
    }
  };

  const handleModeChange = (value: Mode) => {
    setMode(value);
    // TODO M5: 切换为文本时把当前表单序列化后写入 editor baseline；切换为表单时解析当前文本。
  };

  return (
    <>
      {/* 操作栏：左=动作按钮（常显），右=模态切换 */}
      <Toolbar
        className="lt-table-editor-toolbar"
        left={
          <>
            <Tooltip title="验证">
              <Button type="text" icon={<TablerIcon icon={IconFirewallCheck} strokeWidth={2} />} />
            </Tooltip>
            <Tooltip title="格式化">
              <Button type="text" icon={<TablerIcon icon={IconBraces} />} />
            </Tooltip>
            <Tooltip title={readonly ? '抢锁' : '释放锁'}>
              <Button
                type="text"
                icon={<TablerIcon icon={readonly ? IconLock : IconLockOpen} />}
                onClick={handleLockAction}
              />
            </Tooltip>
          </>
        }
        right={
          <Segmented
            value={mode}
            onChange={(v) => handleModeChange(v as Mode)}
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
          <TableFormEditor
            fileId={fileId}
            initialDdl={content}
            readonly={readonly}
            onDirty={(dirty) => setDirtyFlags((prev) => ({ ...prev, [fileId]: dirty }))}
          />
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
