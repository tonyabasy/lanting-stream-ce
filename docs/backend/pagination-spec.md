# 分页查询规范

## 背景

项目已使用 MyBatis-Plus，分页能力直接使用其内置的 `PaginationInnerInterceptor`，
不引入额外的分页插件（如 PageHelper），避免同时维护两套分页机制。

## 依赖说明

MyBatis-Plus 自 3.5.9 版本起将分页插件拆分为独立模块，需单独引入依赖
（项目当前 MyBatis-Plus 版本为 3.5.16，属于此范围）：

```xml
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-jsqlparser</artifactId>
</dependency>
```

建议在根 `pom.xml` 的 `dependencyManagement` 中统一管理版本，与其他 MyBatis-Plus
相关依赖保持一致。

## 数据库支持

`PaginationInnerInterceptor` 官方支持的数据库列表中包含 SQLite，因此项目当前使用的
SQLite 数据源可以正常使用该插件，无需额外适配。

## 插件注册

在 `admin/common/config` 下注册分页插件，并指定方言为 SQLite：

```java
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor(DbType.SQLITE);
        // 单页最多 500 条，系统兜底红线，防止恶意/误操作传超大 pageSize 打爆数据库
        paginationInterceptor.setMaxLimit(500L);

        interceptor.addInnerInterceptor(paginationInterceptor);
        return interceptor;
    }
}
```

## 请求参数：`PageQuery`

放置于 `common` 模块，各业务模块的查询 DTO 继承此基类：

```java
public class PageQuery {

    @Min(1)
    private Integer pageNum = 1;

    @Min(1)
    @Max(100) // 业务建议上限，与插件层 maxLimit 构成两道防线
    private Integer pageSize = 10;
}
```

```java
// 业务模块查询条件示例
public class JobQueryDTO extends PageQuery {
    private String jobName;
    private String status;
}
```

## 响应结构：`PageResult<T>`

**不直接将 MyBatis-Plus 的 `IPage`/`Page` 对象暴露给 Controller 或前端**，避免把 ORM
框架的实现细节泄漏到 API 契约中。自行封装统一的分页响应结构，置于 `common` 模块：

```java
public class PageResult<T> {

    private List<T> records;
    private long total;
    private long pageNum;
    private long pageSize;
    private long totalPages;
    private boolean hasMore;

    public static <T> PageResult<T> of(IPage<T> page) {
        PageResult<T> result = new PageResult<>();
        result.records = page.getRecords();
        result.total = page.getTotal();
        result.pageNum = page.getCurrent();
        result.pageSize = page.getSize();
        result.totalPages = page.getPages();
        result.hasMore = false;
        return result;
    }
}
```

## 使用规范

- Service 层方法返回 `PageResult<T>`，而不是 `IPage`/`Page`；内部转换统一通过
  `PageResult.of(...)` 收口，确保 MyBatis-Plus 的分页对象**不跨出 Service 层**。
- Mapper 层直接使用 `BaseMapper` 自带的 `selectPage` 方法，无需手写分页 SQL：

```java
Page<JobSubmission> page = new Page<>(query.getPageNum(), query.getPageSize());
Page<JobSubmission> result = jobSubmissionMapper.selectPage(page, queryWrapper);
return PageResult.of(result);
```

- 对无法高效获取总记录数的场景（如 JGit `git log` 游标分页），使用 `PageResult.ofHasMore(...)`，
  此时 `total` 与 `totalPages` 固定为 `-1`，调用方通过 `hasMore` 判断是否继续翻页。

## 最大页大小：两道防线

| 层级 | 限制值 | 作用 |
|---|---|---|
| DTO 校验（`PageQuery.pageSize` 上的 `@Max`） | 100 | 业务建议上限，参数校验阶段即拦截，覆盖正常业务场景 |
| 分页插件（`PaginationInnerInterceptor.setMaxLimit`） | 500 | 系统兜底红线，防止绕过 DTO 校验（如直接调用 Mapper）导致的异常大查询 |

两道防线的阈值不要求一致，DTO 层从业务合理性出发设定，插件层从系统防御角度设定。

## 待讨论 / 后续事项

- [ ] 是否需要支持排序参数（`orderBy`/`orderDirection`）在 `PageQuery` 中统一定义，
      并防范 SQL 注入风险（排序字段需做白名单校验）
- [ ] 大数据量场景下是否需要支持游标分页（cursor-based），作为 `PageQuery` 的替代方案
