# 事件驱动架构规范

## 1. 设计目标

系统中存在许多重要事件（如集群更新/删除、任务启动/停止），这些事件需要其他模块联动响应。事件驱动模式能够在**不引入模块间耦合**的前提下实现这一需求。

**事件机制不保证一定能够成功，但能保证在出现异常时不会影响业务流程。**

## 2. 底层机制

基于 Spring Framework 内建的 `ApplicationEventPublisher` + `@EventListener`，**零额外依赖**。

## 3. 目录结构

事件类和 Listener 均放在对应模块的 `event/` 子包下，与 entity/dto/service 同级：

```
admin/src/main/java/com/lanting/admin/module/{模块名}/event/
    ├── XxxEvent.java          # 事件类
    └── XxxEventListener.java  # 监听器（按需创建）
```

## 4. 事件定义规范

- **命名**：`{实体}{动作}Event`，如 `ClusterUpdatedEvent`、`JobStoppedEvent`
- **基类**：继承 `org.springframework.context.ApplicationEvent`
- **注解**：必须标注 `@Getter` + `@ToString`（用于日志排查）
- **字段**：携带 Listener 所需的业务信息，`final` 不可变，构造器赋值
- **source**：构造器第一个参数传 `this`（即发布者）

示例：

```java
@Getter
@ToString
public class ClusterUpdatedEvent extends ApplicationEvent {
    private final Long clusterId;
    private final String clusterName;
    private final String status;

    public ClusterUpdatedEvent(Object source, Long clusterId, String clusterName, String status) {
        super(source);
        this.clusterId = clusterId;
        this.clusterName = clusterName;
        this.status = status;
    }
}
```

## 5. 发布事件

通过 `BasicServiceImpl` 提供的 `publishEventSafely()` 方法发布，异常已被基类统一 catch 并记录日志，**不中断业务流程**。

任何继承 `BasicServiceImpl` 的 Service 直接调用即可：

```java
publishEventSafely(new ClusterUpdatedEvent(this, entity.getId(), entity.getName(), entity.getStatus()));
```

**必须在数据库操作成功后调用。**

## 6. 监听事件

使用 `@EventListener` 注解，方法参数类型自动匹配。如需异步执行（不阻塞 HTTP 响应），配合 `@Async`：

```java
@Component
public class ClusterEventListener {

    @Async
    @EventListener
    public void onClusterUpdated(ClusterUpdatedEvent event) {
        // 处理逻辑...
    }
}
```

- 默认同步执行，加 `@Async` 后异步（需主类标注 `@EnableAsync`）
- Listener 内部应自行 try-catch，一个 Listener 报错不应影响其他 Listener
