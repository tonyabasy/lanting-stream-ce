# 权限设计规范

## 背景

项目使用 Sa-Token JWT 模式做认证，权限体系基于 Sa-Token 的 `StpInterface` 扩展点实现。
不引入 RBAC 角色表，直接用 `superAdminFlag` 字段区分普通用户和超管，保持社区版的最小复杂度。

---

## 核心决策

| 决策项 | 选择 | 说明 |
|---|---|---|
| 权限校验方式 | `StpInterface` + `@SaCheckPermission` 注解 | 比在方法里手写 `checkSuperAdmin()` 更规范，易扩展 |
| 角色体系 | 无独立角色表，只有 `superAdminFlag` 字段 | 社区版用户关系简单，不需要 RBAC |
| 认证方式 | Sa-Token JWT Simple 模式 | 无状态，不依赖 Redis |

---

## 权限标识符命名规范

格式：`{模块}:{操作}`

| 权限标识符 | 含义 | 拥有者 |
|---|---|---|
| `user:admin` | 用户模块管理操作 | 超管 |
| `cluster:admin` | 集群模块管理操作 | 超管 |
| `datasource:admin` | 数据源模块管理操作 | 超管 |
| `job:admin` | 作业模块管理操作 | 超管 |
| `udf:admin` | UDF 模块管理操作 | 超管 |

**规则**：
- 超管（`superAdminFlag = true`）拥有所有 `*:admin` 权限
- 普通用户只有登录权限，没有任何 `*:admin` 权限
- 新增模块时，在此表追加对应的权限标识符

---

## 接口访问分级

```
无需鉴权（Sa-Token 放行）
├── POST /api/auth/login
└── POST /api/auth/logout

需要登录（@SaCheckLogin 或拦截器默认拦截）
├── GET  /api/auth/current          获取当前用户
└── GET  /api/users                 用户列表（所有登录用户可访问）

需要管理员权限（@SaCheckPermission）
├── user:admin
│   ├── POST   /api/users           创建用户
│   ├── PUT    /api/users/{id}      编辑用户
│   ├── DELETE /api/users/{id}      删除用户
│   ├── PUT    /api/users/{id}/super-admin  设置超管标记
│   └── PUT    /api/users/{id}/password     重置密码
├── cluster:admin  → 集群管理接口
├── datasource:admin → 数据源管理接口
└── ...

当前用户自身操作（登录即可，Service 层校验身份）
├── PUT /api/users/me/profile       更新个人资料
└── PUT /api/users/me/password      修改密码
```

---

## 实现方式

### StpInterfaceImpl

```java
@Component
public class StpInterfaceImpl implements StpInterface {

    @Autowired
    private UserService userService;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        UserEntity user = userService.getById(Long.parseLong(loginId.toString()));
        if (user == null) return Collections.emptyList();
        // 超管拥有所有模块的 admin 权限
        if (Boolean.TRUE.equals(user.getSuperAdminFlag())) {
            return List.of("user:admin", "cluster:admin", "datasource:admin",
                           "job:admin", "udf:admin", "file:admin", "monitor:admin");
        }
        return Collections.emptyList();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        // 不使用角色体系，返回空列表
        return Collections.emptyList();
    }
}
```

### Controller 注解用法

```java
// 只需要登录
@SaCheckLogin
@GetMapping("/api/users")
public Result<PageResult<UserEntity>> listUsers(UserQueryDTO query) { ... }

// 需要管理员权限
@SaCheckPermission("user:admin")
@PostMapping("/api/users")
public Result<UserEntity> createUser(@Valid @RequestBody CreateUserDTO dto) { ... }

// 无需任何注解（放行接口在 Sa-Token 拦截器配置里排除）
@PostMapping("/api/auth/login")
public Result<UserEntity> login(@Valid @RequestBody LoginDTO dto) { ... }
```

### Sa-Token 拦截器配置

```java
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handler -> StpUtil.checkLogin()))
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/auth/logout"
                );
    }
}
```

---

## CE/EE 扩展点说明

当前权限模型极简（只有超管/非超管），是社区版的有意设计。
企业版可能需要细粒度权限（如"只能管理自己创建的集群"）或多租户隔离。

记录到 `extension-points-watchlist.md`：权限体系是潜在的 CE/EE 扩展点，
EE 可通过实现自己的 `StpInterface` Bean（`@Primary`）覆盖 CE 的默认实现，
CE 代码不需要改动。

---

## 待讨论 / 后续事项

- [ ] 超管权限列表是否考虑动态化（现在是硬编码字符串列表，新增模块时需要手动维护）
- [ ] 是否需要"查看"和"编辑"两级权限区分（如 `cluster:read` vs `cluster:admin`）
