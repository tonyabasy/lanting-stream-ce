# ResultCode 设计规范

## 背景

后端统一异常处理需要一套标准化的错误码体系（原命名 `ErrorCode`，已更名为 `ResultCode`，
原因：该接口未来也会承载“成功”状态码（如 `SUCCESS(0, ...)`），命名为 `ResultCode` 更准确地
表达“业务结果码”这一语义，而不仅限于错误场景）。

## 核心决策

| 决策项 | 选择 | 说明 |
|---|---|---|
| HTTP 状态码策略 | 错误码映射真实 HTTP 状态 | 不采用“统一 200 + body 内 code”的做法，HTTP 状态码本身承载语义（400/401/403/404/500 等） |
| 载体形式 | 接口 + 每模块独立 enum 实现 | 避免单一巨型 enum 导致多人协作时的 Git 合并冲突 |
| 国际化 | 暂不实现，但预留扩展位 | `code` 本身作为天然的国际化 key，不在接口层加字段，扩展点放在使用方（如 `GlobalExceptionHandler`） |
| 错误码格式 | 5 位数字，首位分大类，按模块分段 | 见下方编码规则 |

## 接口设计

```java
public interface ResultCode {

    /**
     * 业务结果码
     */
    int getCode();

    /**
     * 默认提示信息（中文硬编码）。
     * 后续如接入国际化，由调用方（如 GlobalExceptionHandler）优先通过
     * code 查找 MessageSource 中的多语言文案，查不到再 fallback 到此默认值，
     * 接口本身不需要为此新增字段。
     */
    String getMessage();

    /**
     * 对应的 HTTP 状态码
     */
    int getHttpStatus();
}
```

## 编码规则

5 位数字，首位表示大类：

| 首位 | 大类 | 说明 |
|---|---|---|
| 1 | 参数 / 客户端错误 | 如参数校验失败，对应 HTTP 400 |
| 2 | 认证鉴权 | 登录失效、无权限，对应 HTTP 401 / 403，与 Sa-Token 拦截配合 |
| 3 | 业务规则冲突 | 各业务模块的领域错误，如“用户名已存在”、“作业不存在” |
| 5 | 系统内部错误 | 数据库异常、第三方调用失败等，对应 HTTP 500 |

`3` 开头按模块分段，每个模块预留 100 个码位（理论上足够，超出再扩位）：

| 区间 | 模块 |
|---|---|
| 30101–30199 | user |
| 30201–30299 | cluster |
| 30301–30399 | datasource |
| 30401–30499 | job |
| 30501–30599 | udf |
| 30601–30699 | ai |
| 30701–30799 | file |
| 30801–30899 | monitor |
| 30901–30999 | test    |
| 31001–31099 | publish（含 review） |

> 新增模块时在此表追加区间，避免不同模块的开发者各自瞎编号导致冲突。

## 文件组织

- `CommonResultCode`：放置跨模块通用的错误码（参数校验、鉴权失败、系统内部错误等），
  对应 1xxxx / 2xxxx / 5xxxx 段。
- 各业务模块自行维护 `XxxResultCode`，实现 `ResultCode` 接口，对应该模块在 3xxxx 段中
  预留的区间。例如 `module/user/UserResultCode.java`、`module/job/JobResultCode.java`。

### 关于存放位置的说明

项目当前同时存在两处可能的归属：

1. `admin/src/main/java/com/lanting/admin/common/`（admin 模块内的 common 包，目前为空）
2. 项目根目录独立的 `common` Maven 模块（目前为空的独立模块）

`ResultCode` 接口本身属于纯粹的公共契约，**建议放在根目录的 `common` 模块**，因为：

- 该模块作为独立 Maven 模块，理论上可被 `admin` 之外的其他模块（如未来拆分的微服务、
  独立的 SDK）复用；
- `admin/common` 包目前更适合存放 admin 模块私有的基础设施（如全局异常处理器本身，
  因为它依赖 Spring MVC 的 `@RestControllerAdvice`，与 admin 这个 Web 应用强绑定）。

即：`ResultCode` 接口 + 各模块 `XxxResultCode` 枚举放在 `common` 模块；
`GlobalExceptionHandler`、`BusinessException` 等与 Web 层绑定的部分放在 `admin/common`。
这一点待 `common` 模块的定位在团队内部最终确认后可调整。

## 示例

```java
// common 模块
public enum CommonResultCode implements ResultCode {
    SUCCESS(0, "成功", 200),
    PARAM_INVALID(10001, "参数校验失败", 400),
    UNAUTHORIZED(20001, "未登录或登录已过期", 401),
    FORBIDDEN(20002, "无权限访问", 403),
    SYSTEM_ERROR(50001, "系统内部错误", 500);

    private final int code;
    private final String message;
    private final int httpStatus;

    // 构造器 / getter 省略
}
```

```java
// admin 模块 module/user 包下
public enum UserResultCode implements ResultCode {
    USER_NOT_FOUND(30101, "用户不存在", 404),
    USERNAME_DUPLICATE(30102, "用户名已存在", 400);

    private final int code;
    private final String message;
    private final int httpStatus;

    // 构造器 / getter 省略
}
```

## 待讨论 / 后续事项

- [ ] `common` 模块的最终定位（是否被其他模块依赖、是否需要拆分 `common-core` / `common-web`）
- [ ] `BusinessException` 的设计（是否支持动态参数替换 message，如 `"用户 {0} 不存在"`）
- [ ] `GlobalExceptionHandler` 的实现（如何根据 `ResultCode.getHttpStatus()` 设置响应状态码）
- [ ] `Result` 统一返回结构的设计（与本文档配套，body 中是否仍保留 `code` 字段用于前端细粒度判断）
