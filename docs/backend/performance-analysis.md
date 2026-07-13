# 后端性能分析专题

> 记录后端各模块中已识别的性能瓶颈，统一进行分析、测试与优化。
> 每个问题独立成节，包含问题描述、影响范围、测试方案和优化方案。
> 优先级：🔴 高（影响正常使用）/ 🟡 中（负载上升后出现）/ 🟢 低（边缘场景）

---

## 目录

- [PERF-001 文件树全量遍历](#perf-001-文件树全量遍历)

---

## PERF-001 文件树全量遍历

**模块**：file  
**优先级**：🟡 中  
**状态**：待处理

### 问题描述

`GitFileService.tree()` 每次调用都全量递归遍历磁盘目录树，对每个文件夹节点调用一次 `Files.list()`，整棵树遍历完才返回响应。

核心代码路径：

```
GET /api/files/tree
  → GitFileService.tree()
    → Files.list(root)          // 遍历根目录
      → buildTreeNode(path)     // 递归遍历每个子目录
        → Files.list(subdir)    // 每个文件夹一次磁盘 IO
        → fileLockService.getHolder()  // 每个节点一次内存查询（快）
```

### 影响范围

- **文件数量**：SQL 文件场景下文件数量通常不大（几十到几百），单次遍历在 SSD 上约 10–50ms，机械硬盘或 Docker 挂载卷可能达到 100ms 以上。
- **并发请求**：多用户同时刷新文件树时，每个请求独立做全量磁盘遍历，并发高时磁盘 IO 打满，响应时间线性劣化。
- **触发频率**：取决于前端刷新策略——如果是定时轮询，问题更严重；如果是写操作后主动刷新，影响相对可控。

### 测试方案

**基准测试（单请求延迟）**

```bash
# 构造测试数据：在工作空间下创建 N 个 .sql 文件
for i in $(seq 1 500); do
  mkdir -p ./data/workspaces/default/sql/dir_$((i % 20))
  echo "SELECT $i" > ./data/workspaces/default/sql/dir_$((i % 20))/query_$i.sql
done

# 测量单次 tree 接口响应时间
curl -o /dev/null -s -w "Total: %{time_total}s\n" \
  -H "lanting-token: <token>" \
  http://localhost:8080/api/files/tree
```

**并发测试（吞吐量与响应时间分布）**

使用 [wrk](https://github.com/wg/wrk) 或 [hey](https://github.com/rakyll/hey)：

```bash
# 20 并发，持续 30 秒
hey -n 1000 -c 20 \
  -H "lanting-token: <token>" \
  http://localhost:8080/api/files/tree
```

**关注指标**：

| 指标 | 可接受阈值 | 优化目标 |
|---|---|---|
| P50 响应时间 | < 100ms | < 20ms |
| P99 响应时间 | < 500ms | < 100ms |
| 20 并发下吞吐量 | > 50 req/s | > 200 req/s |
| 磁盘 IO 利用率 | < 50% | < 10%（缓存命中后） |

### 优化方案

详见 [lanting-fs-spec.md](./lanting-fs-spec.md)。

核心思路：引入 `lanting_file_index` 索引表，DB 作为 source of truth，`tree()` 改为 DB 查询（< 1ms），彻底消除磁盘遍历。

文件树变化的时机是确定的——只有 `save`（新文件）、`createFolder`、`delete`、`revert`、`rollbackRelease` 这几个写操作才会改变目录结构。在写操作完成后标记缓存失效，`tree()` 优先返回缓存。

关键设计点：

- 缓存只缓存**目录结构**（`name`/`path`/`type`/`children`），不缓存锁状态
- 每次返回时实时填充 `lockedBy`/`lockedAt`（内存操作，无磁盘 IO）
- 缓存失效标记用 `volatile boolean` 保证可见性
- 排序可以每次在返回前做（纯内存操作，可忽略）

预期效果：缓存命中时响应时间降至 < 5ms，磁盘 IO 降至几乎为零。

#### 方案 B：WebSocket 增量推送（长远方案）

与其让前端轮询拉全量树，不如服务端在文件树变化时主动推送增量更新（节点新增/删除/重命名）。前端维护本地树状态，服务端只推送 diff。

与 `event-driven-architecture-spec.md` 中的事件驱动架构天然契合：写操作发布 `FileTreeChangedEvent`，WebSocket Handler 订阅并推送给在线用户。

成本较高，建议在方案 A 解决并发问题后，作为用户体验优化单独立项。

### 备注

- 方案 A 和方案 B 不互斥，可以先做 A 解决性能问题，再做 B 提升体验。
- 实施方案 A 前需确认前端的文件树刷新策略（定时轮询 vs 写操作后主动刷新），影响缓存失效策略的设计细节。
