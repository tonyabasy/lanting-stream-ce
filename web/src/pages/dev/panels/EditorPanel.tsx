import { useRef } from 'react';
import { Empty } from 'antd';
import { useModel } from 'umi';
import FileTabs from '../editor/FileTabs';
import CodeEditor, { type CodeEditorRef } from '../editor/CodeEditor';
import ReadOnlyBanner from '../editor/ReadOnlyBanner';
import '../index.css';
import '../editor/index.css';

const EditorPanel: React.FC = () => {
  const editor = useModel('editor');
  const {
    openTabs,
    activeTabId,
    setActiveTabId,
    baselineDocs,
    dirtyFlags,
    setDirtyFlags,
    isFileEditable,
    checkClean,
    autoSave,
    acquireLock,
    closeTab,
  } = editor;

  const editable = activeTabId !== null && isFileEditable(activeTabId);
  const activeTab = openTabs.find((t) => t.fileId === activeTabId);

  const codeEditorRef = useRef<CodeEditorRef>(null);

  const handleSwitchTab = async (fileId: number) => {
    // 切换前保存当前激活 tab 的未保存内容
    if (activeTabId !== null && activeTabId !== fileId && dirtyFlags[activeTabId] && isFileEditable(activeTabId)) {
      await codeEditorRef.current?.saveTab(activeTabId);
    }
    setActiveTabId(fileId);
  };

  const handleCloseTab = async (fileId: number) => {
    // 关闭当前激活 tab 时，如有未保存内容则先保存
    if (fileId === activeTabId && dirtyFlags[fileId] && isFileEditable(fileId)) {
      await codeEditorRef.current?.saveTab(fileId);
    }
    closeTab(fileId);
  };

  return (
    <div className="lt-panel-base lt-editor-panel">
      <FileTabs
        tabs={openTabs}
        activeTabId={activeTabId}
        dirtyFlags={dirtyFlags}
        onSwitch={handleSwitchTab}
        onClose={handleCloseTab}
      />
      {openTabs.length === 0 ? (
        <div className="lt-editor-empty">
          <Empty />
        </div>
      ) : (
        <>
          <div className="lt-editor-divider" />
          {activeTab && !editable && (
            <ReadOnlyBanner activeTab={activeTab} acquireLock={acquireLock} />
          )}
          <CodeEditor
            ref={codeEditorRef}
            activeTabId={activeTabId}
            editable={editable}
            baselineDocs={baselineDocs}
            setDirtyFlags={setDirtyFlags}
            checkClean={checkClean}
            autoSave={autoSave}
          />
        </>
      )}
    </div>
  );
};

export default EditorPanel;
