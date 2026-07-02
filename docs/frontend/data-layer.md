# 前端数据层规范

> 适用范围：`src/types/`、`src/services/`、`src/utils/request.ts`
> 原则：**数据层只做数据，不做 UI 决策**

---

## 一、目录职责划分

```
src/
├── types/          ← TS 类型定义（接口入参、返回值、枚举）
├── services/       ← API 调用函数（一个模块一个文件）
├── utils/
│   └── request.ts  ← axios 实例 + 拦截器（不改动，不扩展）
└── hooks/
    └── useXxx.ts   ← 封装 loading/error/data，页面直接调用
```

**三条边界规则：**

1. `types/` 只放类型，不放函数
2. `services/` 只调用 `request`，不调用其他 service，不操作 UI（不调用 message、Modal）
3. UI 反馈（Toast、弹窗、跳转）在 Hook 或页面层处理，不在 service 层处理

---

## 二、types/ 规范

### 文件组织

```
types/
├── api.ts          ← 后端通用结构（ApiResponse、PageResult）
├── auth.ts         ← 认证相关类型
├── job.ts          ← 作业相关类型
├── cluster.ts      ← 集群相关类型
├── datasource.ts   ← 数据源相关类型
└── common.ts       ← 前端公用类型（SelectOption、TableColumn 等）
```

**命名规则：**

| 场景 | 命名方式 | 示例 |
|------|----------|------|
| 后端返回的实体 | `XxxVO` | `JobVO`、`ClusterVO` |
| 请求入参 | `XxxParams` | `LoginParams`、`ListJobParams` |
| 创建/更新表单 | `CreateXxxDTO`、`UpdateXxxDTO` | `CreateJobDTO` |
| 前端内部类型 | 语义名即可 | `SelectOption`、`NavItem` |

### 已有基础类型（不重复定义）

```ts
// types/api.ts — 已定义，所有 service 返回值从这里推导

interface ApiResponse<T = unknown> {
  code: number;
  message: string;
  data: T;
}

interface PageResult<T> {
  records: T[];
  total: number;
  pageNum: number;
  pageSize: number;
  totalPages: number;
}
```

`request.ts` 的响应拦截器已经解包 `data.data`，service 函数的返回类型直接写业务数据类型，**不需要再套 `ApiResponse<T>`**：

```ts
// ✅ 正确
export const getJob = (id: number): Promise<JobVO> =>
  request.get(`/jobs/${id}`);

// ❌ 错误，ApiResponse 已在拦截器解包
export const getJob = (id: number): Promise<ApiResponse<JobVO>> =>
  request.get(`/jobs/${id}`);
```

### 分页请求入参基类

所有分页接口的入参继承这个基类：

```ts
// types/api.ts 追加
export interface PageParams {
  pageNum:  number;  // 从 1 开始
  pageSize: number;  // 默认 20
}
```

---

## 三、services/ 规范

### 文件组织

一个业务模块一个文件，文件名与模块名一致：

```
services/
├── auth.ts         ← 登录、登出、当前用户
├── job.ts          ← 作业 CRUD、启停
├── cluster.ts      ← 集群管理
├── datasource.ts   ← 数据源管理
├── catalog.ts      ← 元数据目录
└── user.ts         ← 用户管理
```

### 函数命名规则

| 操作 | 前缀 | 示例 |
|------|------|------|
| 获取单条 | `get` | `getJob(id)`  |
| 获取列表/分页 | `list` | `listJobs(params)` |
| 新建 | `create` | `createJob(dto)` |
| 更新 | `update` | `updateJob(id, dto)` |
| 删除 | `delete` | `deleteJob(id)` |
| 操作/动作 | 动词 | `startJob(id)`、`stopJob(id)` |

### 函数写法

每个函数只做一件事：调用接口、声明类型、返回 Promise。

```ts
// services/job.ts

import request from '@/utils/request';
import type { PageParams, PageResult } from '@/types/api';
import type { JobVO, CreateJobDTO, UpdateJobDTO, ListJobParams } from '@/types/job';

export const listJobs = (params: ListJobParams & PageParams): Promise<PageResult<JobVO>> =>
  request.get('/jobs', { params });

export const getJob = (id: number): Promise<JobVO> =>
  request.get(`/jobs/${id}`);

export const createJob = (dto: CreateJobDTO): Promise<JobVO> =>
  request.post('/jobs', dto);

export const updateJob = (id: number, dto: UpdateJobDTO): Promise<JobVO> =>
  request.put(`/jobs/${id}`, dto);

export const deleteJob = (id: number): Promise<void> =>
  request.delete(`/jobs/${id}`);

export const startJob = (id: number): Promise<void> =>
  request.post(`/jobs/${id}/start`);

export const stopJob = (id: number): Promise<void> =>
  request.post(`/jobs/${id}/stop`);
```

### service 层禁止事项

```ts
// ❌ 不在 service 里弹提示
import { message } from 'antd';
export const deleteJob = async (id: number) => {
  await request.delete(`/jobs/${id}`);
  message.success('删除成功');   // 禁止
};

// ❌ 不在 service 里做路由跳转
import { history } from 'umi';
export const login = async (params) => {
  const result = await request.post('/auth/login', params);
  history.push('/');             // 禁止
  return result;
};

// ❌ 不在 service 里调用另一个 service
export const createJobAndStart = async (dto) => {
  const job = await createJob(dto);
  await startJob(job.id);        // 禁止，这是业务逻辑，放 hook 里
  return job;
};
```

---

## 四、hooks/ 数据 Hook 规范

service 函数是纯粹的网络调用，**页面不直接调用 service**，而是通过 Hook 调用。Hook 负责管理 loading、error、触发 UI 反馈。

### 命名规则

- 查询类：`useXxx`（如 `useJobs`、`useJob`）
- 操作类：`useXxxActions`（如 `useJobActions`）

### 查询 Hook 模板

```ts
// hooks/useJobs.ts
import { useState, useEffect } from 'react';
import { message } from 'antd';
import { listJobs } from '@/services/job';
import type { JobVO, ListJobParams } from '@/types/job';
import type { PageParams, PageResult } from '@/types/api';

export function useJobs(params: ListJobParams & PageParams) {
  const [data, setData]       = useState<PageResult<JobVO> | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError]     = useState<Error | null>(null);

  useEffect(() => {
    setLoading(true);
    listJobs(params)
      .then(setData)
      .catch((err) => {
        setError(err);
        message.error(err.message ?? '加载失败');
      })
      .finally(() => setLoading(false));
  }, [params.pageNum, params.pageSize]);

  return { data, loading, error };
}
```

### 操作 Hook 模板

```ts
// hooks/useJobActions.ts
import { message } from 'antd';
import { startJob, stopJob, deleteJob } from '@/services/job';

export function useJobActions(onSuccess?: () => void) {
  const start = async (id: number) => {
    try {
      await startJob(id);
      message.success('作业已启动');
      onSuccess?.();
    } catch (err: any) {
      message.error(err.message ?? '启动失败');
    }
  };

  const stop = async (id: number) => {
    try {
      await stopJob(id);
      message.success('作业已停止');
      onSuccess?.();
    } catch (err: any) {
      message.error(err.message ?? '停止失败');
    }
  };

  const remove = async (id: number) => {
    try {
      await deleteJob(id);
      message.success('删除成功');
      onSuccess?.();
    } catch (err: any) {
      message.error(err.message ?? '删除失败');
    }
  };

  return { start, stop, remove };
}
```

### 页面里的用法

```tsx
// pages/jobs/index.tsx
import { useJobs } from '@/hooks/useJobs';
import { useJobActions } from '@/hooks/useJobActions';

const JobsPage: React.FC = () => {
  const { data, loading } = useJobs({ pageNum: 1, pageSize: 20 });
  const { start, stop, remove } = useJobActions(() => {
    // 操作成功后刷新列表
  });

  return (
    <Table
      loading={loading}
      dataSource={data?.records}
      // ...
    />
  );
};
```

---

## 五、错误处理分层

```
request.ts 拦截器
  ├─ 401 → 清 token + 跳转登录页（硬跳转）
  ├─ 403 → 抛出 ApiError(20002)
  ├─ 400 → 抛出 ApiError(body.code, body.message)
  ├─ 500 → 抛出 ApiError(50001, '服务器异常')
  └─ 网络错误 → 抛出 ApiError(-1, '网络连接失败')

service 层
  └─ 不 catch，让错误向上传递

hook 层
  ├─ catch ApiError → message.error(err.message)
  └─ 特殊业务错误码 → 按 code 做差异化处理

页面层
  └─ 一般不需要 catch，hook 已处理
  └─ 表单提交等需要感知结果的场景可以 try/catch hook 返回的 Promise
```

---

## 六、检查清单

新增一个接口时，按顺序检查：

```
□ 1. types/ 里有对应的入参和返回值类型
□ 2. service 函数只有一行，返回类型正确
□ 3. 没有在 service 里写 message / history / 其他 service 调用
□ 4. 页面通过 hook 调用，不直接调用 service
□ 5. loading 状态传给了对应的 UI 组件
□ 6. 错误在 hook 层用 message.error 处理
```
