import { useCallback, useEffect, useRef, useState } from 'react';
import { useModel } from 'umi';
import { Text } from '@codemirror/state';
import type { FileTreeNode } from '@/types/file';
import { loadContent, saveContent } from '@/services/fileTree';

/**
 * 编辑器状态 model — 管理打开的文件 Tab、内容和编辑状态。
 */
export default () => {
  const { initialState } = useModel('@@initialState');
  const currentUserId = initialState?.currentUser?.id as string | undefined;
  const fileLock = useModel('fileLock');

  const [openTabs, setOpenTabs] = useState<FileTreeNode[]>([]);
  const [activeTabId, setActiveTabId] = useState<number | null>(null);

  // 自动保存：CM6 Text 作为 baseline，用于 Text.eq() 快速判脏
  const [baselineDocs, setBaselineDocs] = useState<Record<number, Text>>({});
  const [dirtyFlags, setDirtyFlags] = useState<Record<number, boolean>>({});
  const saveGenerationRef = useRef<Record<number, number>>({});

  /** 判断文件是否可编辑（当前用户持有锁） */
  const isReadonly = useCallback((fileId: number): boolean => {
    if (!currentUserId) return true;
    return fileLock.locks[fileId]?.lockedBy !== currentUserId;
  }, [fileLock.locks, currentUserId]);

  /** 加载文件内容（如已缓存则跳过） */
  const loadFile = useCallback(async (fileId: number) => {
    setBaselineDocs((prev) => {
      if (prev[fileId] !== undefined) return prev;
      loadContent(fileId).then((content) => {
        setBaselineDocs((cur) => {
          if (cur[fileId] !== undefined) return cur;
          return { ...cur, [fileId]: Text.of(content.split(/\n/)) };
        });
        setDirtyFlags((cur) => ({ ...cur, [fileId]: false }));
      });
      return prev;
    });
  }, []);

  /** 打开文件：已在 tabs 中则切换，否则新增 tab 并加载内容 */
  const openFile = useCallback((node: FileTreeNode) => {
    loadFile(node.fileId);
    // 拉取最新锁状态（不再依赖树节点上的 lockedBy）
    fileLock.whoLocked(node.fileId).catch(() => {});
    setOpenTabs((prev) => {
      const exists = prev.find((t) => t.fileId === node.fileId);
      if (exists) {
        setActiveTabId(node.fileId);
        return prev;
      }
      setActiveTabId(node.fileId);
      return [...prev, node];
    });
  }, [loadFile, fileLock]);

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

      // 清理该文件缓存状态，防止内存无限增长
      setBaselineDocs((cur) => {
        const { [fileId]: _, ...rest } = cur;
        return rest;
      });
      setDirtyFlags((cur) => {
        const { [fileId]: _, ...rest } = cur;
        return rest;
      });

      return next;
    });
  }, []);

  // ── 保存 + 锁感知 ──

  /** 保存文件：调 API 写盘，成功后更新缓存。
   *  返回 true 表示成功，false 表示锁已丢失。 */
  const saveFile = useCallback(async (fileId: number, content: string): Promise<boolean> => {
    try {
      await saveContent(fileId, content);
      return true;
    } catch (e: any) {
      // 锁被接管或未持锁
      if (e?.data?.code === 30709 || e?.data?.code === 423) {
        // 刷新本地锁状态（锁已被他人持有）
        fileLock.whoLocked(fileId).catch(() => {});
        return false;
      }
      throw e;
    }
  }, [fileLock]);

  /** idle 时检查是否已 undo 回原位，是则清 dirty 并返回 true */
  const checkClean = useCallback((fileId: number, doc: Text): boolean => {
    const baseline = baselineDocs[fileId];
    if (baseline && doc.eq(baseline)) {
      setDirtyFlags((prev) => ({ ...prev, [fileId]: false }));
      return true;
    }
    return false;
  }, [baselineDocs]);

  /**
   * 自动保存。
   * - 未持锁直接返回 false
   * - 发送前 snapshot doc，成功后以 snapshot 更新 baseline
   * - 用 saveGeneration 防止慢请求覆盖新的 dirty 状态
   * - force=true 时忽略 doc.eq(baseline) 检查，用于用户显式 Ctrl+S / 切换/关闭 tab 前
   */
  const autoSave = useCallback(
    async (fileId: number, snapshot: Text, force = false): Promise<boolean> => {
      if (!isReadonly(fileId)) return false;

      // 非强制保存时，若当前 doc 与 baseline 一致则跳过
      if (!force) {
        const baseline = baselineDocs[fileId];
        if (baseline && snapshot.eq(baseline)) return false;
      }

      const gen = (saveGenerationRef.current[fileId] ?? 0) + 1;
      saveGenerationRef.current = { ...saveGenerationRef.current, [fileId]: gen };

      try {
        const ok = await saveFile(fileId, snapshot.toString());
        if (!ok) return false;
        if (saveGenerationRef.current[fileId] !== gen) return false;
        setBaselineDocs((prev) => ({ ...prev, [fileId]: snapshot }));
        setDirtyFlags((prev) => ({ ...prev, [fileId]: false }));
        return true;
      } catch (e: any) {
        return false;
      }
    },
    [isReadonly, saveFile, baselineDocs],
  );

  /** 抢锁 */
  const acquireLock = useCallback(async (fileId: number, _path: string) => {
    await fileLock.acquire(fileId);
  }, [fileLock]);

  /** 释放锁 */
  const releaseLock = useCallback(async (fileId: number, _path: string) => {
    await fileLock.release(fileId);
  }, [fileLock]);

  // ── beforeunload 保护 ──

  useEffect(() => {
    const handler = (e: BeforeUnloadEvent) => {
      const hasDirty = Object.values(dirtyFlags).some(Boolean);
      if (hasDirty) {
        e.preventDefault();
      }
    };
    window.addEventListener('beforeunload', handler);
    return () => window.removeEventListener('beforeunload', handler);
  }, [dirtyFlags]);

  return {
    openTabs,
    activeTabId,
    setActiveTabId,
    baselineDocs,
    dirtyFlags,
    setDirtyFlags,
    isReadonly,
    loadFile,
    saveFile,
    checkClean,
    autoSave,
    acquireLock,
    releaseLock,
    openFile,
    closeTab,
  };
};
