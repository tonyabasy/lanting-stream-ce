# 前端 fileLock model 重构执行计划

> 状态：待执行
> 创建时间：2026-08-06
> 背景：锁状态从前端各处分散管理（FileTreeNode.lockedBy / editor openTabs / fileTree model）收敛为**全局唯一**的 `fileLock` model。tree 不再维护/提供任何锁相关服务。

---

## 设计原则

1. **锁状态全局唯一**：`models/fileLock.ts` 是查看/修改锁状态的**唯一入口**
2. **tree 不碰锁**：FileTreeNode 的锁字段不再被前端消费；三点菜单删除抢锁/释放锁
3. **抢锁/释放锁入口移到 Toolbar**（编辑器操作栏）
4. 锁状态只有一份，抢/放/查询都同步到所有消费方（editor.readonly、EditorPanel、Status 等）

---

## 一、新建 `models/fileLock.ts`

锁状态唯一来源：

```ts
import { useState, useCallback } from 'react';
import { getLock as getLockApi, acquireLock as acquireLockApi, releaseLock as releaseLockApi } from '@/services/fileTree';

export default () => {
  const [locks, setLocks] = useState<Record<number, { lockedBy?: string; lockedAt?: number }>>({});

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
```

**说明**：
- `locks` 直接暴露，消费方读 `locks[fileId]?.lockedBy / lockedAt`
- `whoLocked` = 查询（只读后端 → 更新本地）；`acquire/release` = 操作（改变锁 → 更新本地）
- acquire 成功后以 `whoLocked` 后端返回为准（软锁抢占时持锁人 = 当前用户）

---

## 二、改动清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `models/fileLock.ts` | 🆕 新建 | 锁状态唯一来源 |
| `models/fileTree.ts` | 修改 | 删 `acquireLock` / `releaseLock`（及 services import）|
| `models/editor.ts` | 修改 | ① `isReadonly` 改查 `fileLock.locks[fileId].lockedBy === currentUserId` ② `acquireLock`/`releaseLock` 改调 `fileLock.acquire/release` ③ `openFile` 改调 `fileLock.whoLocked(fileId)` ④ saveFile 锁丢失标记（`'<lost>'`）改走 fileLock |
| `pages/dev/components/FileTree/FileTreeContent.tsx` | 修改 | 删三点菜单 `lock`/`unlock` case + `acquireLock`/`releaseLock` 解构 |
| `pages/dev/components/FileTree/FileMenuItems.tsx` | 修改 | 删 lock/unlock 菜单项；`getFileMenuItems(isMyLock)` 简化（不再需要 isMyLock 参数）|
| `pages/dev/panels/EditorPanel.tsx` | 修改 | `readonly` 计算改查 `fileLock.locks[activeTabId]` |
| Toolbar（编辑器操作栏）| 修改 | 新增「抢锁 / 释放锁」按钮（入口迁移）|

---

## 三、各文件细节

### 3.1 models/editor.ts

```ts
const fileLock = useModel('fileLock');

/** 判断文件是否只读（当前用户未持有锁） */
const isReadonly = useCallback((fileId: number): boolean => {
  if (!currentUserId) return true;
  return fileLock.locks[fileId]?.lockedBy !== currentUserId;
}, [fileLock.locks, currentUserId]);

/** 抢锁（入口：Toolbar） */
const acquireLock = useCallback(async (fileId: number) => {
  await fileLock.acquire(fileId);
}, [fileLock]);

/** 释放锁（入口：Toolbar） */
const releaseLock = useCallback(async (fileId: number) => {
  await fileLock.release(fileId);
}, [fileLock]);
```

- `openTabs` 不再存/更新 lockedBy（打开文件时 `fileLock.whoLocked(fileId)` 拉最新）
- saveFile 失败（锁被抢占）→ `fileLock.whoLocked(fileId)` 刷新本地（替代 `'<lost>'` 魔法值）

### 3.2 FileTreeContent.tsx

- 三点菜单只保留：重命名 / 移动 / 删除（删 lock/unlock）
- 删 `acquireLock` / `releaseLock` 从 fileTree model 的解构

### 3.3 FileMenuItems.tsx

```ts
// 简化：不再需要 isMyLock
export const getFileMenuItems = (): MenuProps['items'] => [
  { key: 'rename', label: '重命名', ... },
  { key: 'move', label: '移动', ... },
  { key: 'delete', label: '删除', ... },
];
```

### 3.4 EditorPanel.tsx

```ts
const { isReadonly } = useModel('editor');
const readonly = activeTabId === null || isReadonly(activeTabId);
```

> 不直接读 fileLock.locks——readonly 判断统一走 editor.isReadonly（内部查 fileLock）。

### ~~3.5 Toolbar（抢锁/释放锁入口）~~

~~- 编辑器操作栏（EditorToolbar）新增按钮：~~
~~  - 未持有锁 → 「抢锁」（IconLock）~~
~~  - 持有锁 → 「释放锁」（IconLockOpen）~~
~~- 点击调 `editor.acquireLock(fileId)` / `editor.releaseLock(fileId)`~~

---

## 四、执行顺序

1. 新建 `models/fileLock.ts`
2. `models/fileTree.ts` 删锁操作
3. `models/editor.ts` 锁逻辑改走 fileLock
4. `FileTreeContent.tsx` / `FileMenuItems.tsx` 删菜单锁项
5. `EditorPanel.tsx` readonly 改用 editor.isReadonly
6. tsc + 手动验证（抢锁→readonly 解除→释放→readonly 恢复）

---

## 五、明确不做

- tree 接口后端删 lockedBy/lockedAt 字段（本次前端先不消费，后端字段保留；后续可清理）
- 锁图标的树内展示（现状就没有）
- 多 tab 锁状态批量刷新（打开文件时才 getLock）
