import {forwardRef, useEffect, useImperativeHandle, useRef} from 'react';
import type {Text} from '@codemirror/state';
import {Compartment, EditorState, Transaction} from '@codemirror/state';
import {EditorView, highlightActiveLine, keymap, lineNumbers} from '@codemirror/view';
import {defaultKeymap, history, historyKeymap} from '@codemirror/commands';
import {sql} from '@codemirror/lang-sql';
import {message} from 'antd';
import {autoSaveExtension} from './autoSaveExtension';
import './index.css';

const editableCompartment = new Compartment();

const cmTheme = EditorView.theme({
  '.cm-scroller': {
    overflow: 'auto',
    fontFamily: 'var(--ant-font-family-code), monospace',
    fontSize: 'var(--ant-font-size)',
    lineHeight: '1.6',
  },
  '.cm-gutters': {
    fontFamily: 'var(--ant-font-family-code), monospace !important',
    fontSize: 'var(--ant-font-size) !important',
    background: 'var(--ant-color-bg-container) !important',
  },
});

export interface CodeEditorRef {
  /** 立即保存指定 tab 的当前内容（用于切换/关闭 tab 前兜底） */
  saveTab: (fileId: number) => Promise<boolean>;
}

interface CodeEditorProps {
  activeTabId: number | null;
  /** 只读状态（EditorPanel 从锁状态计算传入；true = 只读不可编辑） */
  readonly: boolean;
  baselineDocs: Record<number, Text>;
  setDirtyFlags: React.Dispatch<React.SetStateAction<Record<number, boolean>>>;
  checkClean: (fileId: number, doc: Text) => boolean;
  autoSave: (fileId: number, snapshot: Text, force?: boolean) => Promise<boolean>;
}

const CodeEditor = forwardRef<CodeEditorRef, CodeEditorProps>(
  ({ activeTabId, readonly, baselineDocs, setDirtyFlags, checkClean, autoSave }, ref) => {
    const editorHostRef = useRef<HTMLDivElement>(null);
    const editorViewRef = useRef<EditorView | null>(null);
    const lastLoadedTabRef = useRef<number | null>(null);
    const propsRef = useRef<CodeEditorProps>({
      activeTabId,
      readonly,
      baselineDocs,
      setDirtyFlags,
      checkClean,
      autoSave,
    });
    propsRef.current = { activeTabId, readonly, baselineDocs, setDirtyFlags, checkClean, autoSave };

    useImperativeHandle(ref, () => ({
      saveTab: async (fileId: number) => {
        const view = editorViewRef.current;
        if (!view || Number(view.dom.dataset.activeFileId) !== fileId) return false;
        return propsRef.current.autoSave(fileId, view.state.doc);
      },
    }));

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
                  const fileId = Number(id);
                  propsRef.current
                    .autoSave(fileId, view.state.doc, true)
                    .then((ok) => {
                      if (!ok) message.warning('文件只读，请先抢锁');
                    });
                  return true;
                },
                preventDefault: true, // 阻止浏览器默认行为（保存网页）
              },
            ]),
            sql(),
            editableCompartment.of(EditorView.editable.of(false)),
            autoSaveExtension({
              onDirty: (fileId) =>
                propsRef.current.setDirtyFlags((prev) => ({ ...prev, [fileId]: true })),
              onClean: (fileId, doc) => propsRef.current.checkClean(fileId, doc),
              onSave: (fileId, snapshot) => propsRef.current.autoSave(fileId, snapshot),
            }),
            cmTheme,
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
        effects: editableCompartment.reconfigure(EditorView.editable.of(!readonly)),
      });
    }, [readonly]);

    useEffect(() => {
      const view = editorViewRef.current;
      if (!view) return;

      if (activeTabId === null) {
        lastLoadedTabRef.current = null;
        return;
      }

      view.dom.dataset.activeFileId = String(activeTabId);

      const baseline = baselineDocs[activeTabId];
      if (!baseline) return;

      // 只有真正切换/打开 tab 时才 dispatch；保存引起的 baseline 更新不触发，
      // 避免覆盖用户在保存期间的新输入。
      if (lastLoadedTabRef.current === activeTabId) return;

      if (!view.state.doc.eq(baseline)) {
        view.dispatch({
          changes: { from: 0, to: view.state.doc.length, insert: baseline },
          annotations: Transaction.userEvent.of('programmatic'),
        });
      }
      lastLoadedTabRef.current = activeTabId;
    }, [activeTabId, baselineDocs]);

    return <div className="lt-editor-body" ref={editorHostRef} />;
  },
);

CodeEditor.displayName = 'CodeEditor';

export default CodeEditor;
