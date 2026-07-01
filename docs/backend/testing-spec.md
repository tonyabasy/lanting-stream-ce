# 测试规范

> 本文档面向 AI 辅助开发场景，目的是让 AI 在生成或建议测试代码时做出正确判断。
> 核心立场：**测试是手段不是目的，只写有价值的测试，不追求覆盖率指标。**

---

## 测试分层体系

规范的 Spring Boot 项目测试分三层，各司其职：

```
        ┌─────────────────────────┐
        │   集成测试 @SpringBootTest │  ← 少量，验证完整链路
        ├─────────────────────────┤
        │  切片测试 @WebMvcTest 等  │  ← 适量，验证框架行为
        ├─────────────────────────┤
        │   单元测试 Mockito        │  ← 多量，验证业务逻辑
        └─────────────────────────┘
```

### 第一层：单元测试

**工具**：`@ExtendWith(MockitoExtension.class)` + Mockito，不启动 Spring 上下文。

**测什么**：有分支、有边界、出错难发现的业务逻辑（Service 层）。

**不测什么**：简单 CRUD、纯数据类、配置类、Mapper。

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock
    private UserMapper userMapper;
    @InjectMocks
    private UserService userService;

    @Test
    void createUser_shouldThrow_whenUsernameAlreadyExists() {
        when(userMapper.selectOne(any())).thenReturn(existingUser);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.createUser(dto));
        assertEquals(UserResultCode.USERNAME_DUPLICATE, ex.getResultCode());
    }
}
```

---

### 第二层：切片测试

Spring Boot 提供的"切片测试"只加载特定层的最小上下文，比 `@SpringBootTest` 快，又能验证真实的框架行为（注解、序列化、校验等），是单元测试和集成测试之间的重要补充。

#### Controller 层：`@WebMvcTest`

**测什么**：
- 路由是否正确（URL、HTTP 方法）
- `@Valid` 参数校验是否生效，非法入参能否返回 400
- 响应体序列化是否正确（字段名、类型、`@JsonIgnore` 是否生效）
- `GlobalExceptionHandler` 的异常到 HTTP 状态码映射是否正确
- Sa-Token 鉴权拦截是否生效

**不测什么**：业务逻辑（Mock 掉 Service）。

```java
@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Test
    void login_shouldReturn400_whenUsernameBlank() throws Exception {
        mockMvc.perform(post("/api/user/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"\",\"password\":\"123456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10001));
    }

    @Test
    void login_shouldReturn200_whenValid() throws Exception {
        UserEntity mockUser = new UserEntity();
        mockUser.setUsername("admin");
        when(userService.login(any())).thenReturn(mockUser);

        mockMvc.perform(post("/api/user/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.password").doesNotExist()); // 验证敏感字段不暴露
    }
}
```

#### Mapper 层：`@MybatisPlusTest`

**测什么**：复杂的自定义 SQL、多表查询、分页逻辑。

**不测什么**：MyBatis-Plus 自动生成的 `selectById`、`insert` 等基础方法，测的是框架本身。

---

### 第三层：集成测试

**工具**：`@SpringBootTest`，启动完整 Spring 上下文。

**测什么**：跨层的完整链路（Controller → Service → Mapper → 真实数据库），用于验证层与层之间的协作，以及配置类、Bean 装配是否正确。

**什么时候写**：
- 某个链路被反复改出 bug，切片测试已经覆盖不到
- 准备发版前的回归验证

**注意**：集成测试启动慢（数秒），数量要少，不要把所有测试都写成 `@SpringBootTest`。

---

## 各层职责对照表

| 测试层 | 工具 | 典型场景 | 启动速度 |
|---|---|---|---|
| 单元测试 | Mockito | Service 业务规则、工具类 | 毫秒级 |
| Controller 切片 | `@WebMvcTest` + MockMvc | 路由、校验、序列化、鉴权 | 秒级（部分上下文） |
| Mapper 切片 | `@MybatisPlusTest` | 复杂自定义 SQL | 秒级（部分上下文） |
| 集成测试 | `@SpringBootTest` | 完整链路、Bean 装配 | 数秒（完整上下文） |

---

## 本项目中的具体判断

| 类/方法 | 测试层 | 原因 |
|---|---|---|
| `UserService.createUser`（用户名重复） | 单元测试 | 业务规则，有分支 |
| `buildSearchWrapper` | 单元测试 | 多字段多分支，容易漏 |
| `UserController` 所有接口 | Controller 切片 | 验证路由、校验、序列化、鉴权 |
| `GlobalExceptionHandler` | Controller 切片 | 异常到 HTTP 状态码映射容易配错，随 `@WebMvcTest` 一起验证 |
| `UserMapper.listUsersNotGroupAdmin` | Mapper 切片 | 自定义 SQL，需验证正确性 |
| `UserService.deleteUser` | 不写 | 逻辑极简，切片测试覆盖即可 |
| `Result` 静态工厂方法 | 不写 | 就是 new 对象，没有逻辑 |
| `UserMapper` 基础 CRUD | 不写 | 测框架，不测业务 |
| Entity / DTO | 不写 | 纯数据类 |

---

## 怎么写才是对的

### 原则一：测行为，不测实现

验证"给定输入，得到什么输出/抛什么异常"，不验证"内部调用了哪些方法几次"。

```java
// 正确：测行为
@Test
void createUser_shouldThrow_whenUsernameAlreadyExists() {
    when(userMapper.selectOne(any())).thenReturn(existingUser);
    assertThrows(BusinessException.class, () -> userService.createUser(dto));
}

// 错误：测实现（过度 Mock，一旦重构就得改测试）
@Test
void createUser_shouldCallMapperOnce() {
    userService.createUser(dto);
    verify(userMapper, times(1)).selectOne(any());
}
```

### 原则二：只 Mock 外部依赖

数据库、网络、文件系统才 Mock，业务逻辑本身不 Mock。Controller 切片测试中 Mock 掉 Service 是正确的，因为 Service 对 Controller 层来说就是"外部依赖"。

### 原则三：一个测试只验证一个场景

方法名直接描述场景和期望结果：

```java
// 正确
createUser_shouldThrow_whenUsernameAlreadyExists()
login_shouldReturn400_whenPasswordBlank()
login_shouldNotExposePassword_inResponse()

// 错误
testCreateUser()
test1()
```

### 原则四：覆盖边界值和异常路径，不只测 happy path

```
// 登录接口至少要测：
- 用户名为空 → 400
- 密码为空 → 400
- 用户不存在 → 对应业务错误码
- 密码错误 → 对应业务错误码
- 正常登录 → 200，返回 token，不含 password 字段
```

---

## 什么是错的做法

### 错误一：为覆盖率而测
追求覆盖率指标，结果大量测试是在测 getter/setter 和框架代码。AI 生成测试时尤其容易犯这个错。

### 错误二：过度 Mock
把 Service 里所有依赖都 Mock 掉，测试变成了验证"调用了哪些方法"，重构时测试先炸。

### 错误三：测试代码比实现还复杂
需要 50 行 setup 才能测 5 行业务代码，说明要么业务代码依赖太多（需要重构），要么这段代码根本不值得单独测（换用切片测试或集成测试）。

### 错误四：把所有测试都写成 `@SpringBootTest`
`@SpringBootTest` 启动慢，应该只用于真正需要完整上下文的场景。能用切片测试解决的不要用 `@SpringBootTest`，能用单元测试解决的不要用切片测试。

### 错误五：用 curl 手工验证替代自动化 Controller 测试
curl 是开发时的临时验证手段，不可重复、不能进 CI、改了代码也不会自动报警。Controller 层的验证应该用 `@WebMvcTest` + MockMvc 自动化。

---

## AI 生成测试时的检查清单

在生成任何测试代码之前，先逐项确认：

- [ ] 这段逻辑应该用哪一层测试？（单元 / 切片 / 集成）
- [ ] 测试在验证行为还是实现？如果是实现，重写
- [ ] Mock 的是外部依赖还是业务逻辑？如果是业务逻辑，去掉 Mock
- [ ] 方法名能清晰描述场景和期望结果吗？
- [ ] 是否覆盖了边界值和异常路径，不只是 happy path？
- [ ] 测试之间是否相互独立？
- [ ] 有没有用 `@SpringBootTest` 做本可以用切片测试完成的事？
