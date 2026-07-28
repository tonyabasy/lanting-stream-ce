import { useEffect, useRef } from 'react';
import { EditorState, Compartment } from '@codemirror/state';
import { EditorView, keymap, lineNumbers, highlightActiveLine } from '@codemirror/view';
import { defaultKeymap, history, historyKeymap } from '@codemirror/commands';
import { sql } from '@codemirror/lang-sql';
import { useModel } from 'umi';
import { Modal, Button, message } from 'antd';
import { IconLock } from '@tabler/icons-react';
import FileTabs from './FileTabs';
import '../index.css';
import './index.css';

const editableCompartment = new Compartment();

const EditorPanel: React.FC = () => {
  const editor = useModel('editor');
  const { openTabs, activeTabId, fileContents, isFileEditable, saveFile, acquireLock, switchTab, closeTab } = editor;

  const editorHostRef = useRef<HTMLDivElement>(null);
  const editorViewRef = useRef<EditorView | null>(null);
  const editorRef = useRef(editor);
  editorRef.current = editor;

  const editable = activeTabId !== null && isFileEditable(activeTabId);
  const activeTab = openTabs.find((t) => t.fileId === activeTabId);

  useEffect(() => {
    if (!editorHostRef.current) return;

    const view = new EditorView({
      state: EditorState.create({
        doc: '',
        extensions: [
          lineNumbers(),
          highlightActiveLine(),
          history(),
          keymap.of([
            ...defaultKeymap,
            ...historyKeymap,
            {
              key: 'Mod-s',
              run: () => {
                const id = view.dom.dataset.activeFileId;
                if (!id) return true;
                const e = editorRef.current;
                handleSave(Number(id), view, e);
                return true;
              },
              preventDefault: true,
            },
          ]),
          sql(),
          editableCompartment.of(EditorView.editable.of(false)),
          EditorView.updateListener.of(() => {}),
        ],
      }),
      parent: editorHostRef.current,
    });

    editorViewRef.current = view;

    return () => {
      view.destroy();
      editorViewRef.current = null;
    };
  }, []);

  useEffect(() => {
    const view = editorViewRef.current;
    if (!view) return;
    view.dispatch({
      effects: editableCompartment.reconfigure(EditorView.editable.of(editable)),
    });
  }, [editable]);

  useEffect(() => {
    const view = editorViewRef.current;
    if (!view || activeTabId === null) return;

    view.dom.dataset.activeFileId = String(activeTabId);

    const content = fileContents[activeTabId];
    if (content === undefined) return;

    const current = view.state.doc.toString();
    if (current !== content) {
      view.dispatch({
        changes: { from: 0, to: view.state.doc.length, insert: content },
      });
    }
  }, [activeTabId, fileContents]);

  const handleSave = async (fileId: number, view: EditorView, e: typeof editor) => {
    const tab = e.openTabs.find((t) => t.fileId === fileId);
    if (!tab) return;

    if (!e.isFileEditable(fileId)) {
      Modal.confirm({
        title: '未锁定文件',
        content: '你未锁定此文件，当前修改无法保存。是否抢锁并保存？',
        okText: '抢锁并保存',
        cancelText: '取消',
        onOk: async () => {
          try {
            await e.acquireLock(fileId, tab.path);
            message.success('抢锁成功');
            const ok = await e.saveFile(fileId, view.state.doc.toString());
            if (ok) message.success('保存成功');
          } catch (err: any) {
            message.error(err?.message || '抢锁失败');
          }
        },
      });
      return;
    }

    const ok = await e.saveFile(fileId, view.state.doc.toString());
    if (ok) {
      message.success('保存成功');
    } else {
      message.error('保存失败：锁已被他人接管，编辑器已切换为只读');
    }
  };

  return (
    <div className="lt-panel-base lt-editor-panel">
      <FileTabs
        tabs={openTabs}
        activeTabId={activeTabId}
        onSwitch={switchTab}
        onClose={closeTab}
      />
      <div className="lt-editor-divider" />
      {activeTab && !editable && (
        <div className="lt-editor-readonly-banner">
          <IconLock size={12} />
          <span>只读模式{activeTab.lockedBy ? ` · ${activeTab.lockedBy} 正在编辑` : ''}</span>
          <Button
            type="link"
            size="small"
            onClick={async () => {
              try {
                await acquireLock(activeTab.fileId, activeTab.path);
                message.success('抢锁成功，现在可以编辑了');
              } catch (err: any) {
                message.error(err?.message || '抢锁失败');
              }
            }}
          >
            抢锁
          </Button>
        </div>
      )}
      <div className="lt-editor-body" ref={editorHostRef} />
    </div>
  );
};

export default EditorPanel;
