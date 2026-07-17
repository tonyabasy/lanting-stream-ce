# GitFileService Code Review Checklist

> 共 30 个方法，按功能分组。

## 公共方法（Public API）

### 文件树
- [ ] ① `tree(String parentPath, String sort)` — 文件树查询

### 读取
- [ ] ② `content(Long fileId)` — 读取磁盘当前文件内容

### 写入
- [ ] ③ `create(CreateFileDTO dto)` — 创建空文件
- [ ] ④ `save(SaveFileDTO dto)` — 自动保存到磁盘
- [ ] ⑤ `createFolder(CreateFolderDTO dto)` — 创建文件夹
- [ ] ⑥ `rename(RenameDTO dto)` — 重命名文件或文件夹

### 删除与回收站
- [x] ⑦ `delete(Long fileId, boolean force)` — 删除文件/文件夹（软删除）
- [ ] ⑧ `trash(String parentPath)` — 回收站文件树
- [ ] ⑨ `purge(Long fileId)` — 从回收站彻底删除
- [ ] ⑩ `restore(RestoreFileDTO dto)` — 恢复已删除文件/文件夹

### 提交
- [x] ⑪ `commit(CommitFileDTO dto)` — 提交文件到 Git

### 历史与对比
- [ ] ⑫ `history(HistoryPageQuery query)` — 查询 Git 文件历史（游标分页）
- [ ] ⑬ `diff(Long fileId, String from, String to)` — 计算两 commit 间 unified diff

### 回滚
- [ ] ⑭ `revert(RevertFileDTO dto)` — 文件级软回滚

### 发布
- [ ] ⑮ `publish(PublishDTO dto)` — 发布当前 HEAD（打 tag）
- [ ] ⑯ `deleteTag(String tagName)` — 删除 tag

---

## 私有方法（内部实现）

### 基础设施
- [ ] ⑰ `withWorkspaceLock(Supplier<T>)` — Git 写锁包装，串行化 add/commit/tag
- [ ] ⑱ `resolvePathByFileId(Long fileId)` — 通过 fileId 解析路径（含已删除）
- [ ] ⑲ `validatePath(String path)` — 路径合法性校验（第一道防线）
- [ ] ⑳ `ensureInsideWorkspace(Path target, Path root)` — 工作空间边界校验（第二道防线）
- [ ] ㉑ `validateFileType(Path path)` — 文件扩展名白名单校验
- [ ] ㉒ `relativePath(Path path, Path root)` — 计算相对路径

### 树与排序
- [ ] ㉓ `sortTreeNodes(List<FileTreeNode>, String sort)` — 排序树节点

### Git 操作
- [x] ㉔ `gitCommit(List<String> toAdd, List<String> toRemove, String message)` — 执行 git add/rm + commit
- [ ] ㉕ `readFileFromCommitByPath(Path root, String commitHash, String path)` — 按 commit 读取文件内容
- [ ] ㉖ `readFileFromCommit(Repository, RevCommit, String path)` — 从指定 commit 树中读取文件
- [ ] ㉗ `prepareTreeParser(Repository, RevCommit)` — 构建 diff 用的 TreeParser

### 锁
- [ ] ㉘ `releaseLocksRecursively(Long fileId)` — 递归释放文件及子文件锁

### 辅助
- [ ] ㉙ `generateTagName(Git git)` — 生成 tag 名称
- [ ] ㉚ `getFileHistoryVOs(int pageSize, List<RevCommit>)` — 批量转换 RevCommit → VO

---

**使用方式：** 每 review 完一个方法，告诉我编号或方法名，我帮你把对应项打勾。
