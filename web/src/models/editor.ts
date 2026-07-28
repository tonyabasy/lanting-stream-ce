import { useCallback, useState } from 'react';
import { useModel } from 'umi';
import type { FileTreeNode } from '@/pages/dev/types/file';
import { loadContent, saveContent, acquireLock as acquireLockApi } from '@/services/fileTree';

/**
 * 编辑器状态 model — 管理打开的文件 Tab、内容和编辑状态。
 */
export default () => {
  const { initialState } = useModel('@@initialState');
  const currentUserId = initialState?.currentUser?.id as string | undefined;

  const [openTabs, setOpenTabs] = useState<FileTreeNode[]>([]);
  const [activeTabId, setActiveTabId] = useState<number | null>(null);
  const [fileContents, setFileContents] = useState<Record<number, string>>({});

  /** 判断文件是否可编辑（当前用户持有锁） */
  const isFileEditable = useCallback((fileId: number): boolean => {
    if (!currentUserId) return false;
    const tab = openTabs.find((t) => t.fileId === fileId);
    if (!tab) return false;
    return tab.lockedBy === currentUserId;
  }, [openTabs, currentUserId]);

  /** 加载文件内容（如已缓存则跳过） */
  const loadFile = useCallback(async (fileId: number) => {
    setFileContents((prev) => {
      if (prev[fileId] !== undefined) return prev;
      loadContent(fileId).then((content) => {
        setFileContents((cur) => {
          if (cur[fileId] !== undefined) return cur;
          return { ...cur, [fileId]: content };
        });
      });
      return prev;
    });
  }, []);

  /** 打开文件：已在 tabs 中则切换，否则新增 tab 并加载内容 */
  const openFile = useCallback((node: FileTreeNode) => {
    loadFile(node.fileId);
    setOpenTabs((prev) => {
      const exists = prev.find((t) => t.fileId === node.fileId);
      if (exists) {
        // 更新 lockedBy（可能已变化）
        setOpenTabs((cur) =>
          cur.map((t) =>
            t.fileId === node.fileId ? { ...t, lockedBy: node.lockedBy, lockedAt: node.lockedAt } : t,
          ),
        );
        setActiveTabId(node.fileId);
        return prev;
      }
      setActiveTabId(node.fileId);
      return [...prev, node];
    });
  }, [loadFile]);

  const closeTab = useCallback((fileId: number) => {
    setOpenTabs((prev) => {
      const idx = prev.findIndex((t) => t.fileId === fileId);
      if (idx === -1) return prev;
      const next = prev.filter((t) => t.fileId !== fileId);

      setActiveTabId((current) => {
        if (current !== fileId) return current;
        if (next.length === 0) return null;
        const newIdx = Math.min(idx, next.length - 1);
        return next[newIdx].fileId;
      });

      return next;
    });
  }, []);

  const switchTab = useCallback((fileId: number) => {
    setActiveTabId(fileId);
  }, []);

  // ── 保存 + 锁感知 ──

  /** 保存文件：调 API 写盘，成功后更新缓存。
   *  返回 true 表示成功，false 表示锁已丢失。 */
  const saveFile = useCallback(async (fileId: number, content: string): Promise<boolean> => {
    try {
      await saveContent(fileId, content);
      setFileContents((prev) => ({ ...prev, [fileId]: content }));
      return true;
    } catch (e: any) {
      // 锁被接管或未持锁
      if (e?.data?.code === 30709 || e?.data?.code === 423) {
        // 标记此文件为只读
        setOpenTabs((prev) =>
          prev.map((t) => (t.fileId === fileId ? { ...t, lockedBy: '<lost>' } : t)),
        );
        return false;
      }
      throw e;
    }
  }, []);

  /** 抢锁 */
  const acquireLock = useCallback(async (fileId: number, path: string) => {
    await acquireLockApi(fileId);
    // 刷新 tab 锁状态
    setOpenTabs((prev) =>
      prev.map((t) =>
        t.fileId === fileId ? { ...t, lockedBy: currentUserId } : t,
      ),
    );
  }, [currentUserId]);

  return {
    openTabs,
    activeTabId,
    fileContents,
    isFileEditable,
    loadFile,
    saveFile,
    acquireLock,
    openFile,
    closeTab,
    switchTab,
  };
};
