import { useEffect, useRef } from 'react';
import { EditorState, Compartment } from '@codemirror/state';
import { EditorView, keymap, lineNumbers, highlightActiveLine } from '@codemirror/view';
import { defaultKeymap, history, historyKeymap } from '@codemirror/commands';
import { sql } from '@codemirror/lang-sql';
import { Modal, message } from 'antd';
import type { FileTreeNode } from '@/pages/dev/types/file';

const editableCompartment = new Compartment();

interface CodeEditorProps {
  activeTabId: number | null;
  editable: boolean;
  fileContents: Record<number, string>;
  openTabs: FileTreeNode[];
  isFileEditable: (fileId: number) => boolean;
  saveFile: (fileId: number, content: string) => Promise<boolean>;
  acquireLock: (fileId: number, path: string) => Promise<void>;
}

const CodeEditor: React.FC<CodeEditorProps> = ({
  activeTabId,
  editable,
  fileContents,
  openTabs,
  isFileEditable,
  saveFile,
  acquireLock,
}) => {
  const editorHostRef = useRef<HTMLDivElement>(null);
  const editorViewRef = useRef<EditorView | null>(null);

  // 用 ref 保存最新 props，供 keymap 回调读取
  const propsRef = useRef<CodeEditorProps>({
    activeTabId,
    editable,
    fileContents,
    openTabs,
    isFileEditable,
    saveFile,
    acquireLock,
  });
  propsRef.current = {
    activeTabId,
    editable,
    fileContents,
    openTabs,
    isFileEditable,
    saveFile,
    acquireLock,
  };

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
                handleSave(Number(id), view, propsRef.current);
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

  const handleSave = async (fileId: number, view: EditorView, props: CodeEditorProps) => {
    const tab = props.openTabs.find((t) => t.fileId === fileId);
    if (!tab) return;

    if (!props.isFileEditable(fileId)) {
      Modal.confirm({
        title: '未锁定文件',
        content: '你未锁定此文件，当前修改无法保存。是否抢锁并保存？',
        okText: '抢锁并保存',
        cancelText: '取消',
        onOk: async () => {
          try {
            await props.acquireLock(fileId, tab.path);
            message.success('抢锁成功');
            const ok = await props.saveFile(fileId, view.state.doc.toString());
            if (ok) message.success('保存成功');
          } catch (err: any) {
            message.error(err?.message || '抢锁失败');
          }
        },
      });
      return;
    }

    const ok = await props.saveFile(fileId, view.state.doc.toString());
    if (ok) {
      message.success('保存成功');
    } else {
      message.error('保存失败：锁已被他人接管，编辑器已切换为只读');
    }
  };

  return <div className="lt-editor-body" ref={editorHostRef} />;
};

export default CodeEditor;
