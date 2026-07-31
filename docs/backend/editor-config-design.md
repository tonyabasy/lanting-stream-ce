# Editor Config 设计

> 从用户场景逐层推导：场景 → 业务流 → 模型 → API → 实现。
> 待讨论，当前仅为占位。

---

## 关联关系（从 Table 设计继承）

```
SQL 文件 (fileId=42)  ───→  .flink/conf/42.json
```

- Config 文件名为 SQL 文件的 `fileId` + `.json`
- 新建 SQL 文件不自动创建 Config
- 用户首次点击右侧 ConfigPanel 时才创建 `{}` 空配置
- 重命名/移动 SQL 文件时 `fileId` 不变，Config 自动跟随
- `.flink/` 是完全封禁的隐藏目录，所有文件树视图不展示

---

## 用户场景

（待讨论）
