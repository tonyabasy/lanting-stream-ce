import { useState, useCallback } from 'react';
import { getLock as getLockApi, acquireLock as acquireLockApi, releaseLock as releaseLockApi } from '@/services/fileTree';

/** 单个文件的锁状态 */
export interface FileLockState {
  lockedBy?: string;
  lockedAt?: number;
}

/**
 * 文件锁状态 model — 全局唯一维护锁状态的地方。
 *
 * 所有锁相关操作（查锁 / 抢锁 / 释放锁）和状态都从这里读写，
 * tree / editor / EditorPanel 等消费方统一读 `locks[fileId]`。
 */
export default () => {
  const [locks, setLocks] = useState<Record<number, FileLockState>>({});

  /** 查锁：后端 → 更新本地 map */
  const whoLocked = useCallback(async (fileId: number) => {
    const info = await getLockApi(fileId);
    setLocks((prev) => ({ ...prev, [fileId]: info ?? {} }));
    return info;
  }, []);

  /** 抢锁：API + 以后端返回为准刷新本地 */
  const acquire = useCallback(async (fileId: number) => {
    await acquireLockApi(fileId);
    await whoLocked(fileId);
  }, [whoLocked]);

  /** 释放锁：API + 本地清除 */
  const release = useCallback(async (fileId: number) => {
    await releaseLockApi(fileId);
    setLocks((prev) => {
      const { [fileId]: _, ...rest } = prev;
      return rest;
    });
  }, []);

  return { locks, whoLocked, acquire, release };
};
