import { Empty } from 'antd';
import { useModel } from 'umi';
import FileTabs from '../editor/FileTabs';
import CodeEditor from '../editor/CodeEditor';
import ReadOnlyBanner from '../editor/ReadOnlyBanner';
import '../index.css';
import '../editor/index.css';

const EditorPanel: React.FC = () => {
  const editor = useModel('editor');
  const { openTabs, activeTabId, fileContents, isFileEditable, saveFile, acquireLock, switchTab, closeTab } = editor;

  const editable = activeTabId !== null && isFileEditable(activeTabId);
  const activeTab = openTabs.find((t) => t.fileId === activeTabId);

  return (
    <div className="lt-panel-base lt-editor-panel">
      <FileTabs
        tabs={openTabs}
        activeTabId={activeTabId}
        onSwitch={switchTab}
        onClose={closeTab}
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
            activeTabId={activeTabId}
            editable={editable}
            fileContents={fileContents}
            openTabs={openTabs}
            isFileEditable={isFileEditable}
            saveFile={saveFile}
            acquireLock={acquireLock}
          />
        </>
      )}
    </div>
  );
};

export default EditorPanel;
