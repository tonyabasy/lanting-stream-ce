# 测试规范

> 本文档面向 AI 辅助开发场景，目的是让 AI 在生成或建议测试代码时做出正确判断。
> 核心立场：**测试是手段不是目的，只写有价值的测试，不追求覆盖率指标。**

---

## 核心判断标准

**被测逻辑的边界在哪里，决定用什么测试方式——而不是"用不用 Mock"。**

| 情况 | 做法 |
|---|---|
| 被测逻辑在 Java 代码里，IO 只是副作用 | Mock IO，专注测 Java 逻辑 |
| 被测逻辑本身就是 IO 操作的组合 | 用真实 IO + 隔离环境，不 Mock |
| 需要验证多个组件协作（含 HTTP 层） | `@SpringBootTest` 集成测试，尽量少 |

**本项目的实际指导原则**：

优先使用真实环境测试（`@TempDir` + SQLite + 本地 Git），这个项目的技术栈天然规避了"全不 Mock"的主要风险：

- SQLite 是本地文件，没有连接池和外部服务依赖
- 文件系统和 Git 仓库用 `@TempDir` 隔离，每个测试独立，自动清理
- 不依赖任何网络服务，不存在"外部服务不可用导致测试失败"的问题

因此：
- **Mockito** 只用于纯内存逻辑、不依赖任何 IO 的 Service（如 `FileLockService`）
- **`@TempDir` + 手动构造 Service** 用于 IO 密集型 Service（如 `GitFileService`），不启动 Spring 上下文
- **`@SpringBootTest`** 只用于涉及 Sa-Token 会话/鉴权的场景（Controller 层），因为 Sa-Token 与 `@WebMvcTest` 不兼容

---

## 测试分层体系

```
        ┌──────────────────────────────┐
        │  集成测试 @SpringBootTest      │  ← 少量，含 HTTP 层和鉴权的场景
        ├──────────────────────────────┤
        │  IO 集成测试 @TempDir          │  ← 适量，IO 密集型 Service
        ├──────────────────────────────┤
        │  单元测试 Mockito              │  ← 适量，纯内存业务逻辑
        └──────────────────────────────┘
```

### 第一层：单元测试

**工具**：`@ExtendWith(MockitoExtension.class)` + Mockito，不启动 Spring 上下文。

**适用场景**：被测逻辑是纯 Java 计算，IO 只是副作用（可以被 Mock 掉而不影响测试价值）。

**典型例子**：`FileLockService`（纯内存 ConcurrentHashMap 操作）、`UserService.createUser`（业务规则校验）。

**不适用场景**：IO 本身就是被测逻辑的一部分。Mock 掉文件系统或 Git 仓库，等于把被测逻辑也 Mock 掉了，测试没有意义。

```java
@ExtendWith(MockitoExtension.class)
class FileLockServiceTest {

    private FileLockService lockService = new FileLockService();

    @Test
    void doIfHolder_shouldExecuteAction_whenCallerIsHolder() {
        lockService.acquire("sql/a.sql", "alice");
        String result = lockService.doIfHolder("sql/a.sql", "alice", () -> "executed");
        assertEquals("executed", result);
    }

    @Test
    void doIfHolder_shouldThrow_whenCallerIsNotHolder() {
        lockService.acquire("sql/a.sql", "alice");
        assertThrows(BusinessException.class,
            () -> lockService.doIfHolder("sql/a.sql", "bob", () -> "executed"));
    }
}
```

---

### 第二层：IO 集成测试

**工具**：`@TempDir` + 手动构造 Service，不启动 Spring 上下文。

**适用场景**：Service 的核心逻辑是文件系统、Git 仓库、本地数据库等 IO 操作的组合，无法通过 Mock 有效验证。

**典型例子**：`GitFileService`（Git + 磁盘）、`FileIndexService`（索引 DB + 磁盘）。

**关键优势**：
- 不启动 Spring 容器，启动开销接近零
- `@TempDir` 每个测试独立目录，天然隔离，JUnit 自动清理
- 验证的是真实行为，不是 Mock 出来的假设

```java
@ExtendWith(MockitoExtension.class)
class GitFileServiceIntegrationTest {

    @TempDir
    Path tempDir;

    private GitFileService gitFileService;

    @BeforeEach
    void setUp() throws Exception {
        // 初始化真实 Git 仓库
        Git.init().setDirectory(tempDir.toFile()).call();
        // 手动构造 Service，注入真实依赖
        gitFileService = new GitFileService(/* workspacePath = */ tempDir, ...);
    }

    @Test
    void delete_shouldBeTrackedInGit_afterCommit() throws Exception {
        // 创建文件 → 提交 → 删除 → 提交 → 验证 HEAD tree 中不含该文件
        gitFileService.save(new SaveFileDTO("sql/a.sql", "SELECT 1"));
        gitFileService.commit(new CommitFileDTO(List.of("sql/a.sql"), "add"));
        gitFileService.delete("sql/a.sql", false);

        // 用 JGit 验证 HEAD tree 中文件已不存在
        try (Git git = Git.open(tempDir.toFile());
             RevWalk walk = new RevWalk(git.getRepository())) {
            RevCommit head = walk.parseCommit(git.getRepository().resolve("HEAD"));
            try (TreeWalk treeWalk = TreeWalk.forPath(
                    git.getRepository(), "sql/a.sql", head.getTree())) {
                assertNull(treeWalk, "删除后文件应从 Git tree 中移除");
            }
        }
    }
}
```

---

### 第三层：集成测试

**工具**：`@SpringBootTest`，启动完整 Spring 上下文。

**适用场景**：需要完整 HTTP 链路和 Sa-Token 鉴权的 Controller 测试。

**为什么不用 `@WebMvcTest`**：Sa-Token 的拦截器和 `@SaCheckPermission` 注解处理器依赖完整 Spring 容器，与 `@WebMvcTest` 不兼容，因此 Controller 测试统一使用 `@SpringBootTest`。

**注意**：集成测试启动慢（数秒），数量要少，只测需要完整 HTTP 链路的场景。

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class FileControllerTest extends BaseIntegrationTest {

    @Test
    void save_shouldReturn401_whenNotLoggedIn() {
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/files/save",
            new HttpEntity<>(new SaveFileDTO("sql/a.sql", "SELECT 1"), jsonHeaders()),
            String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }
}
```

---

## 各层职责对照表

| 测试层 | 工具 | 适用场景 | 启动速度 |
|---|---|---|---|
| 单元测试 | Mockito | 纯内存业务逻辑（锁、规则校验） | 毫秒级 |
| IO 集成测试 | `@TempDir` + 手动构造 | IO 密集型 Service（Git、文件系统、索引） | 毫秒级 |
| 集成测试 | `@SpringBootTest` | 含 Sa-Token 鉴权的 HTTP 链路 | 数秒 |

---

## 本项目具体判断

| 类/方法 | 测试层 | 原因 |
|---|---|---|
| `FileLockService` | 单元测试 | 纯内存逻辑，无 IO |
| `UserService.createUser`（用户名重复） | 单元测试 | 业务规则，有分支 |
| `GitFileService` 所有方法 | IO 集成测试 | IO 就是被测逻辑，不能 Mock |
| `FileIndexService` reconcile | IO 集成测试 | 索引 DB + 磁盘扫描，需真实环境 |
| `UserController` / `FileController` | 集成测试 | Sa-Token 鉴权需要完整上下文 |
| `GlobalExceptionHandler` | 集成测试 | 随 Controller 测试一起验证 |
| `UserMapper` 基础 CRUD | 不写 | 测框架，不测业务 |
| Entity / DTO | 不写 | 纯数据类 |
| `WorkspaceService.init` | 不写 | 测的是 JDK 和 JGit 行为，不是业务逻辑 |

---

## 怎么写才是对的

### 原则一：测行为，不测实现

验证"给定输入，得到什么输出/抛什么异常"，不验证"内部调用了哪些方法几次"。

```java
// 正确：测行为
@Test
void delete_shouldBeTrackedInGit() {
    // 验证删除后 Git tree 中文件不存在
}

// 错误：测实现（一旦重构就得改测试）
@Test
void delete_shouldCallGitAddOnce() {
    verify(git.add(), times(1)).call();
}
```

### 原则二：Mock 的判断标准

不是"IO 就 Mock"，而是"IO 是副作用才 Mock，IO 是逻辑就不 Mock"：

```
IO 是副作用（数据库只是存储结果）→ Mock，专注测 Java 逻辑
IO 是逻辑（文件操作、Git 操作本身就是目的）→ 不 Mock，用真实 IO
```

### 原则三：一个测试只验证一个场景

方法名直接描述场景和期望结果：

```java
// 正确
delete_shouldBeTrackedInGit_afterCommit()
save_shouldReturn401_whenNotLoggedIn()
doIfHolder_shouldThrow_whenCallerIsNotHolder()

// 错误
testDelete()
test1()
```

### 原则四：覆盖边界值和异常路径，不只测 happy path

---

## 什么是错的做法

### 错误一：为覆盖率而测
追求覆盖率指标，结果大量测试是在测 getter/setter 和框架代码。

### 错误二：对 IO 密集型 Service 过度 Mock
Mock 掉文件系统或 Git 仓库，等于把被测逻辑也 Mock 掉了，测试验证的只是"我调用了这些方法"，没有业务价值，重构时还会先炸。

### 错误三：测试代码比实现还复杂
需要 50 行 setup 才能测 5 行业务代码，说明要么业务代码依赖太多需要重构，要么这段代码根本不值得单独测。

### 错误四：把所有测试都写成 `@SpringBootTest`
`@SpringBootTest` 启动慢，本项目只在 Controller 层（Sa-Token 鉴权场景）使用，IO 集成测试用 `@TempDir` + 手动构造，不需要 Spring 容器。

### 错误五：测框架行为
`WorkspaceService.init()` 调用 `Files.createDirectories` 和 `Git.init()`，测的是 JDK 和 JGit 的行为，不是业务逻辑，不写。

---

## AI 生成测试时的检查清单

在生成任何测试代码之前，先逐项确认：

- [ ] 被测逻辑的边界在哪里？IO 是副作用还是逻辑本身？
- [ ] 选择正确的测试层：纯内存 → Mockito；IO 密集 → `@TempDir`；HTTP/鉴权 → `@SpringBootTest`
- [ ] 测试在验证行为还是实现？如果是实现，重写
- [ ] Mock 的是外部依赖还是被测逻辑？如果是被测逻辑，去掉 Mock
- [ ] 方法名能清晰描述场景和期望结果吗？
- [ ] 是否覆盖了边界值和异常路径，不只是 happy path？
- [ ] 测试之间是否相互独立？
- [ ] 有没有用 `@SpringBootTest` 做本可以用 `@TempDir` 完成的事？
