# 异常处理与日志规范

## 背景

[`service-layer-spec.md`](service-layer-spec.md) 确立了"Service 层只抛异常，不返回 Result"的原则，
[`result-code-spec.md`](result-code-spec.md) 定义了 `ResultCode` 编码体系和 `GlobalExceptionHandler`
的统一处理框架。但以下问题尚未被这两份文档覆盖：

1. **异常类型单一**：所有错误共用一个 `BusinessException`，无法区分"用户改个参数就行"的业务错误和"需要运维排查磁盘"的系统故障。
2. **日志缺失**：Service 层几乎不记日志（`GitFileService` 1027 行仅 1 条 `log.error`），全局 Handler
   虽然统一 `warn`，但只有 HTTP 路径才经过 Handler——定时任务、MQ 消费者、内部调用出问题零记录。
3. **错误信息泄漏**：`new BusinessException(rc, e.getMessage())` 大量出现，`IOException.getMessage()`
   包含磁盘绝对路径，直接透传给前端暴露服务器文件系统结构。
4. **排查困难**：没有 traceId，用户截图反馈时无法关联到后端日志。

本文档定义本项目的异常分类体系、日志打印位置与格式、以及错误信息如何安全地呈现给前端。

---

## 核心决策

| 决策项 | 选择 | 说明 |
|---|---|---|
| 异常层次 | 两层：`BusinessException` + `SystemException` | 预期内的业务失败 vs 需要人工介入的系统故障 |
| 日志位置 | **抛出点记**，Handler 只做响应组装 | 抛出点拥有完整业务上下文（fileId、path、user），Handler 只有 HTTP 元数据 |
| 追踪 ID | code + traceId 透传前端 | 用户截图反馈时可立即关联日志，方案见 [追踪 ID](#追踪-id) |
| 国际化 | 后端 `MessageSource`，延续当前设计 | `code` 作为 i18n key，查不到 fallback `ResultCode.message` |
| 前端呈现 | 用户永远不看到 `e.getMessage()` | 异常信息分层：`userMessage`（给前端） / `devMessage`（记日志） |

---

## 异常分类体系

### 类层次

```
RuntimeException
├── BusinessException           ← 预期内的业务失败（用户能自行修正的）
│   ├── 场景：参数校验、文件不存在、已被锁定、权限不足
│   ├── 日志：抛出前不记日志，由 Handler 统一处理
│   └── 前端：code + message（i18n 文案）
│
├── ContentInconsistentException  ← 特殊：CRC32 不一致，但仍返回磁盘内容
│   ├── 场景：索引 CRC32 与磁盘实际内容不匹配
│   ├── 日志：抛出前记 WARN
│   └── 前端：code + message + data（磁盘内容）
│
└── SystemException              ← 【新增】非预期系统故障，需要人工排查
    ├── 场景：磁盘读写失败、Git 操作失败、DB 操作失败
    ├── 日志：**抛出前必须记 ERROR**，包含业务上下文 + cause
    └── 前端：50001（通用"系统内部错误"），不暴露内部细节
```

### BusinessException（不变）

`BusinessException` 承载**用户能自行解决的错误**。当前实现已经满足需求，不需要修改：

```java
@Getter
public class BusinessException extends RuntimeException {
    private final ResultCode resultCode;   // 错误码，如 FILE_NOT_FOUND(30702)
    private final Object[] args;           // i18n 占位符参数，如 {0}
    private final String customMessage;    // 覆盖默认 i18n 文案（仅在确有必要时使用）

    // 工厂方法：支持动态参数
    public static BusinessException of(ResultCode resultCode, Object... args) {
        return new BusinessException(resultCode, args);
    }
}
```

**使用约束**：`customMessage` 仅用于真正需要动态消息且 i18n 的 `{0}` 占位符参数无法覆盖的场景。
不应将系统异常信息（如 `e.getMessage()`）塞入此处。

### SystemException（新增）

`SystemException` 承载**需要人工介入的系统故障**。与 `BusinessException` 的关键区别：

| 维度 | BusinessException | SystemException |
|---|---|---|
| 原因 | 用户操作不当 | 系统内部故障 |
| 用户能修吗 | 能（改参数、改文件名） | 不能（需排查磁盘/DB/Git） |
| 日志级别 | WARN | ERROR |
| 告警策略 | 通常不告警 | 可配置告警 |
| 前端消息 | 具体错误说明 | 通用"系统内部错误" |
| HTTP 状态码 | 4xx / 业务码 | 500 |

```java
/**
 * 系统内部异常，表示需要人工介入的非预期故障（磁盘、DB、Git 等基础设施异常）。
 * <p>
 * 与 {@link BusinessException} 的区别：
 * <ul>
 *   <li>BusinessException 是"用户做错了什么"，返回友好提示让用户自行修正；</li>
 *   <li>SystemException 是"系统出问题了"，返回通用错误消息，详细信息记在日志里。</li>
 * </ul>
 * 构造时必须传入原始异常（cause），禁止传入 e.getMessage() 作为用户消息。
 */
@Getter
public class SystemException extends RuntimeException {

    private final ResultCode resultCode;

    /**
     * @param resultCode 错误码（应为 5xxxx 系统错误段或 30708 等特定系统错误）
     * @param cause      原始异常，用于日志记录和根因分析
     */
    public SystemException(ResultCode resultCode, Throwable cause) {
        super(cause);
        this.resultCode = resultCode;
    }
}
```

> **为什么 `SystemException` 不需要 `customMessage`？**
> 面向用户的 message 始终由 `ResultCode` → i18n 决定（"系统内部错误"），面向运维的细节在日志里。
> 保留 `customMessage` 会诱导开发者在抛异常时写面向用户的消息，破坏分层。

---

## 日志规范

### 日志级别使用

| 级别 | 使用场景 | 示例 |
|---|---|---|
| `ERROR` | 需要人工介入的系统异常（磁盘满、DB 挂了、Git 操作失败）；兜底的未预期异常 | `log.error("保存文件失败: fileId={}, path={}", fileId, path, e)` |
| `WARN` | 预期外但系统能自行降级的情况；业务异常中值得关注的事件 | CRC32 不一致、补偿操作失败 |
| `INFO` | 关键业务节点（文件创建、提交、删除）；定时任务开始/结束 | `log.info("文件提交成功: fileId={}, hash={}", fileId, hash)` |
| `DEBUG` | 排查问题时需要的中间状态 | 路径解析过程、锁状态变更 |

### 日志在哪里记

原则：**抛出点记录，全局 Handler 只组装 HTTP 响应。**

```
Service 层
│
├─ 系统故障 ──→ log.error("操作描述: 参数1={}, 参数2={}", v1, v2, exception)
│                throw new SystemException(rc, exception)
│
├─ 数据不一致 ──→ log.warn("内容不一致: fileId={}, expectCrc={}, actualCrc={}",
│                       fileId, expectCrc, actualCrc)
│                  throw new ContentInconsistentException(rc, msg, data)
│
└─ 业务失败 ──→ 不记日志
                 throw new BusinessException(rc)
```

**为什么不在 Handler 记？**

| 对比项 | 抛出点记 | Handler 记 |
|---|---|---|
| 业务上下文（fileId、path、user） | ✅ 完整 | ❌ 只能拿到 code 和 message |
| 异常 cause（堆栈） | ✅ `log.error(..., e)` | ❌ BusinessException 没有 cause |
| 非 HTTP 路径（定时任务等） | ✅ 覆盖 | ❌ 不经过 Handler |
| 格式一致性 | 需要规范 | ✅ 天然一致 |

结论：Handler 不记日志，抛出点拥有全部上下文。

### 日志内容格式

```
log.error("人性化操作描述: 参数1={}, 参数2={}, ...", value1, value2, exception)
```

**必须包含**：
- 人性化操作描述（"保存文件失败"，不是"IOException"）
- 业务关键参数（fileId、path、操作人等）
- 异常对象本身（作为最后参数传入 SLF4J，自动输出堆栈）

**禁止**：
- `log.error(e.getMessage())` — 丢失堆栈且信息不全
- `e.printStackTrace()` — 不走日志框架，无法被日志收集系统采集
- `log.error("操作失败", e)` 但没有业务参数 — 排查时不知道是哪个文件
- 在日志中输出完整的敏感文件内容

### 正确示例 vs 反例

```java
// ❌ 当前反例：泄漏磁盘路径 + 不记日志
} catch (IOException e) {
    throw new BusinessException(FileResultCode.FILE_OPERATION_FAILED, e.getMessage());
}

// ✅ 正确：抛出点记 ERROR + 业务上下文 + cause，前端只看到通用错误
} catch (IOException e) {
    log.error("保存文件失败: fileId={}, path={}, user={}", fileId, path, username, e);
    throw new SystemException(FileResultCode.FILE_OPERATION_FAILED, e);
}

// ✅ 正确：业务校验不记日志
if (entity == null) {
    throw new BusinessException(FileResultCode.FILE_NOT_FOUND);
}
```

---

## 追踪 ID

### 设计

每个 HTTP 请求分配一个唯一的 `traceId`，贯穿整个请求生命周期：

```
请求 → Filter(MDC.put traceId) → Controller → Service → 日志自动带 traceId → 响应
```

前端响应中包含 traceId，用户截图反馈时可以直接定位：

```json
{
  "code": 50001,
  "message": "系统内部错误",
  "traceId": "a1b2c3d4e5f6",
  "data": null
}
```

### 实现要点

1. **Filter 层**：在 `doFilter` 入口生成 `traceId`（UUID 前 12 位即可），放入 `MDC`。
2. **Result 响应体**：增加 `traceId` 字段，Handler 组装响应时从 `MDC` 读取并写入。
3. **日志配置**：`logback-spring.xml` 的 pattern 中加入 `%X{traceId}`，每条日志自动携带。
4. **跨线程传递**：如果存在 `@Async` 或自定义线程池，使用 `MDC.getCopyOfContextMap()` 传递上下文。

### Result 调整

```java
// 失败响应工厂方法增加 traceId 注入
public static <T> Result<T> error(ResultCode resultCode, String message, String traceId) {
    return new Result<>(resultCode.getCode(), message, null, traceId);
}
```

---

## 前端呈现

### 响应结构

```
{
  "code": 30702,                              // 前端做 if/switch 跳转逻辑
  "message": "文件不存在",                      // 前端做 toast / 提示条
  "traceId": "a1b2c3d4",                      // 用户反馈时提供，用于关联后端日志
  "data": null                                // 特殊情况（CRC32 不一致等）时携带内容
}
```

| 字段 | 来源 | 随语言变化 | 用途 |
|---|---|---|---|
| `code` | `ResultCode.getCode()` | 否 | 前端逻辑判断 |
| `message` | `MessageSource` → fallback `ResultCode.message` | 是 | 前端提示文案 |
| `traceId` | Filter 层 `MDC` | 否 | 问题排查 |
| `data` | 异常携带（如 `ContentInconsistentException`） | — | 特殊情况 |

### message 的用户面向原则

`message` 是给**最终用户**看的，不应包含：

- ❌ 磁盘路径：`/var/data/workspaces/default/nested/secret.sql`
- ❌ 技术栈细节：`java.nio.file.AccessDeniedException`
- ❌ 异常消息原文：`Permission denied`

正确示例：
- ✅ "文件不存在"
- ✅ "文件已被锁定"
- ✅ "系统内部错误，请联系管理员（追踪ID: a1b2c3d4）"

### 两类异常的呈现差异

| 场景 | 前端收到 |
|---|---|
| 文件不存在 | `{ code: 30702, message: "文件不存在" }` |
| 磁盘写入失败 | `{ code: 50001, message: "系统内部错误", traceId: "a1b2c3d4" }` |

`SystemException` 只暴露 `ResultCode` 对应的 i18n 消息和 traceId。内部细节（哪个文件、什么错误）全在日志里，运维通过 traceId 回溯。

---

## GlobalExceptionHandler 调整

### 新增 SystemException 处理

```java
/**
 * 系统异常：Service 层已记录完整日志，此处只组装 HTTP 响应，不重复记日志。
 * 返回通用的"系统内部错误"消息，不暴露 e.getCause() 的信息。
 */
@ExceptionHandler(SystemException.class)
public Result<Void> handleSystemException(SystemException e, HttpServletResponse response) {
    ResultCode rc = e.getResultCode();
    response.setStatus(rc.getHttpStatus());
    String traceId = MDC.get("traceId");
    // 不调用 resolveMessage(rc) 返回具体错误，而是返回通用系统错误
    // 具体信息已在 Service 层日志中，用户不应看到内部细节
    return Result.error(
            CommonResultCode.SYSTEM_ERROR,
            resolveMessage(CommonResultCode.SYSTEM_ERROR),
            traceId
    );
}

/**
 * 业务异常：返回 i18n 文案，让用户知道哪里错了、怎么修正。
 */
@ExceptionHandler(BusinessException.class)
public Result<Void> handleBusinessException(BusinessException e, HttpServletResponse response) {
    ResultCode rc = e.getResultCode();
    response.setStatus(rc.getHttpStatus());
    String traceId = MDC.get("traceId");
    String message = e.getCustomMessage() != null
            ? e.getCustomMessage()
            : resolveMessage(rc, e.getArgs());
    log.warn("业务异常: code={}, message={}, traceId={}", rc.getCode(), message, traceId);
    return Result.error(rc, message, traceId);
}
```

### 设计要点

- `BusinessException` → Handler 记 `warn`（因为 Service 层不记），返回具体错误消息
- `SystemException` → Handler 不记日志（Service 层已记 `error`），返回通用"系统内部错误"
- `Exception`（兜底）→ Handler 记 `error` + 完整堆栈，返回通用"系统内部错误"

---

## 国际化

### 方案：后端 MessageSource（延续当前设计）

当前[`result-code-spec.md`](result-code-spec.md) 中的国际化方案已经落地，本文档延续而非替代。核心链路：

```
ResultCode.code → MessageSource.getMessage(code, args, locale) → 返回文案
                                                  ↓ 未找到
                                          fallback: ResultCode.message（中文默认）
```

### 需要补齐

当前 `messages.properties` 和 `messages_en_US.properties` 缺少文件模块（3xxxx 段）的 key。需要补齐文件模块及其他业务模块的 i18n key。

---

## Service 层迁移指南

以当前的典型反例和正确写法对照，用于逐步迁移现有代码：

### 模式 1：IOException 处理

```java
// ❌ 当前
} catch (IOException e) {
    throw new BusinessException(FileResultCode.FILE_OPERATION_FAILED, e.getMessage());
}

// ✅ 目标
} catch (IOException e) {
    log.error("保存文件失败: fileId={}, path={}, user={}", fileId, path, username, e);
    throw new SystemException(FileResultCode.FILE_OPERATION_FAILED, e);
}
```

### 模式 2：GitAPIException 处理

```java
// ❌ 当前
} catch (IOException | GitAPIException e) {
    throw new BusinessException(FileResultCode.FILE_OPERATION_FAILED, e.getMessage());
}

// ✅ 目标
} catch (IOException | GitAPIException e) {
    log.error("Git 操作失败: paths={}, message={}", paths, dto.getMessage(), e);
    throw new SystemException(FileResultCode.FILE_OPERATION_FAILED, e);
}
```

### 模式 3：业务校验

```java
// ✅ 当前即正确，保持不变
if (entity == null || entity.getDeletedAt() > 0) {
    throw new BusinessException(FileResultCode.FILE_NOT_FOUND, String.valueOf(fileId));
}
```

### 模式 4：数据不一致

```java
// ✅ 当前即正确：记 WARN + 抛 ContentInconsistentException
if (entity.getCrc32() != diskCrc32) {
    log.warn("内容不一致: fileId={}, dbCrc32={}, diskCrc32={}", fileId, entity.getCrc32(), diskCrc32);
    throw new ContentInconsistentException(
            FileResultCode.FILE_CONTENT_INCONSISTENT,
            "文件内容与索引不一致，请执行索引修复", content);
}
```

### 模式 5：DB 操作失败

```java
// ❌ 当前（假设存在类似的 DB 异常忽略模式）
try {
    fileIndexService.indexOnSave(path, root, bytes);
} catch (DataAccessException e) {
    throw new BusinessException(FileResultCode.FILE_OPERATION_FAILED, e.getMessage());
}

// ✅ 目标
try {
    fileIndexService.indexOnSave(path, root, bytes);
} catch (DataAccessException e) {
    log.error("索引更新失败: fileId={}, path={}", fileId, path, e);
    throw new SystemException(FileResultCode.FILE_OPERATION_FAILED, e);
}
```

---

## 待讨论 / 后续事项

- [ ] `SystemException` 需要新建类，确定放在 `admin/common/exception` 还是根目录 `common` 模块
- [ ] `Result` 增加 `traceId` 字段，影响所有 `Result.success()` / `Result.error()` 调用点
- [ ] `traceId` Filter 的实现：放在 `common/config` 还是独立的 `common/web` 包
- [ ] 三层分割（`BusinessException` / `SystemException` / `IntegrationException`）是否在未来多外部依赖时引入
- [ ] `ResultCode.getHttpStatus()` 的约定：`SystemException` 是否需要单独的错误码段（如 `5xxxx` 系统段），还是复用现有码
- [ ] 非 HTTP 路径（定时任务等）的异常处理策略——没有 traceId、没有 Handler，如何保证错误不被静默吞掉
- [ ] 敏感信息脱敏规则：日志中 filePath 是否需要脱敏为相对路径或 hash
