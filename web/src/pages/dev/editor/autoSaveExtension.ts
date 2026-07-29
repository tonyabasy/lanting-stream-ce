import type { Text } from '@codemirror/state';
import { Facet, Transaction } from '@codemirror/state';
import { EditorView, ViewPlugin, type ViewUpdate } from '@codemirror/view';

export interface AutoSaveConfig {
  /** 文档变化时置脏 */
  onDirty: (fileId: number) => void;
  /** idle 检查是否已 undo 回原位，返回 true 表示当前已 clean */
  onClean: (fileId: number, doc: Text) => boolean;
  /** 触发保存，返回 true 表示成功落盘 */
  onSave: (fileId: number, snapshot: Text) => Promise<boolean>;
  /** 检查是否 clean 的延迟，默认 300ms */
  checkDelay?: number;
  /** 自动保存延迟，默认 1500ms */
  saveDelay?: number;
}

const autoSaveConfigFacet = Facet.define<AutoSaveConfig, AutoSaveConfig>({
  combine: (configs) => configs[configs.length - 1],
});

class AutoSavePlugin {
  private readonly config: AutoSaveConfig;

  private timers: Record<number, { check?: number; save?: number }> = {};

  constructor(view: EditorView) {
    this.config = view.state.facet(autoSaveConfigFacet);
  }

  /**
   * 用户在编辑器中打字、粘贴、撤销，只要文档内容改变了 CodeMirror 就会调用 update
   */
  update(update: ViewUpdate): void {
    if (!update.docChanged) return;

    // 跳过程序化的内容加载（如切换/打开 tab 时的 dispatch），避免误标 dirty
    const isProgrammatic = update.transactions.every(
      (tr) => tr.annotation(Transaction.userEvent) === 'programmatic',
    );
    if (isProgrammatic) return;

    const fileId = this.activeFileId(update.view);
    if (fileId === null) return;

    this.config.onDirty(fileId);
    this.schedule(fileId, update.view);
  }

  destroy(): void {
    Object.values(this.timers).forEach((t) => {
      if (t.check) window.clearTimeout(t.check);
      if (t.save) window.clearTimeout(t.save);
    });
    this.timers = {};
  }

  private activeFileId(view: EditorView): number | null {
    const raw = view.dom.dataset.activeFileId;
    return raw ? Number(raw) : null;
  }

  private schedule(fileId: number, view: EditorView): void {
    this.clear(fileId);
    const entry = this.timers[fileId] ?? {};
    this.timers[fileId] = entry;

    const { checkDelay = 300, saveDelay = 1500 } = this.config;

    entry.check = window.setTimeout(() => {
      if (this.activeFileId(view) !== fileId) return;
      this.config.onClean(fileId, view.state.doc);
    }, checkDelay);

    entry.save = window.setTimeout(() => {
      if (this.activeFileId(view) !== fileId) return;
      this.config.onSave(fileId, view.state.doc);
    }, saveDelay);
  }

  private clear(fileId: number): void {
    const entry = this.timers[fileId];
    if (!entry) return;
    if (entry.check) window.clearTimeout(entry.check);
    if (entry.save) window.clearTimeout(entry.save);
    entry.check = undefined;
    entry.save = undefined;
  }
}

const autoSavePlugin = ViewPlugin.fromClass(AutoSavePlugin);

/**
 * CodeMirror 6 自动保存扩展。
 *
 * 通过 Facet 注入回调，使自动保存逻辑与 React 组件解耦，便于分屏等
 * 多编辑器实例场景复用。
 */
export function autoSaveExtension(config: AutoSaveConfig) {
  return [autoSaveConfigFacet.of(config), autoSavePlugin];
}
