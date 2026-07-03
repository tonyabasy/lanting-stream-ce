# API 文档注解规范

## 背景

项目使用 SpringDoc OpenAPI（基于 Swagger v3）自动生成 API 文档。只需在代码中标注少量注解，启动后即可通过 `/swagger-ui.html` 查看交互式文档。

依赖已引入：`springdoc-openapi-starter-webmvc-ui`，无需额外配置。

## 设计

### 原则

- 只标注三层：Controller → DTO/Entity 字段 → 通用包装类
- 注解描述用**中文**，和 Javadoc 保持一致
- 已存在的 Javadoc 描述直接搬到注解中即可，不增加认知负担

### 必须写的注解

#### 1. Controller 类 — `@Tag`

```java
@Tag(name = "用户管理")
@RestController
@RequestMapping("/api/users")
public class UserController { ... }
```

`name` 决定 Swagger UI 中的分组名称。不写则所有接口堆在 `default` 分组。

#### 2. Controller 方法 — `@Operation`

```java
@Operation(summary = "分页查询用户列表")
@GetMapping
public Result<PageResult<UserEntity>> listUsers(@Valid UserQueryDTO query) { ... }
```

`summary` 用一句话描述接口功能。不写则文档中只展示 URL，无法快速理解用途。

#### 3. DTO / Entity 字段 — `@Schema`

请求体 DTO 和响应 Entity 的**每个字段**都需要：

```java
@Schema(description = "集群名称")
@NotBlank(message = "集群名称不能为空")
private String name;
```

不写则文档中只显示 Java 字段名（如 `flinkHome`），前端无法理解含义。

类本身也加一句：

```java
@Schema(description = "创建集群请求")
@Data
public class CreateClusterDTO { ... }
```

#### 4. 通用包装类 — `@Schema`

在 `Result<T>` 和 `PageResult<T>` 的字段上加 `description`，因为所有接口都用它们做响应体。

### 不需要写的

以下场景 SpringDoc 能自动解析，不需要手动加注解：

| 场景 | 自动解析来源 |
|------|-------------|
| 请求方式 | `@GetMapping` / `@PostMapping` 等 |
| 请求路径 | `@RequestMapping` + 方法映射 |
| 参数是否必填 | `@NotBlank` / `@NotNull` + `@Valid` |
| 路径参数名 | `@PathVariable` 变量名 |
| 泛型响应体类型 | 方法返回值声明的泛型 |

`@ApiResponse`、`@Parameter`、`example`、`allowableValues` 等属于锦上添花，现阶段不要求写。
