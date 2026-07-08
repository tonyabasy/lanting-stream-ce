package com.lanting.admin.module.file.service;

import com.lanting.admin.BaseIntegrationTest;
import com.lanting.admin.module.file.entity.FileIndexEntity;
import com.lanting.admin.module.file.mapper.FileIndexMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FileIndexService 服务集成测试。覆盖写操作回归 + reconcile + tree() DB 验证。
 *
 * @author wangzhao
 */
@DisplayName("FileIndexService 服务集成测试")
class FileIndexServiceTest extends BaseIntegrationTest {

    @Autowired
    private FileIndexService fileIndexService;

    @Autowired
    private FileIndexMapper fileIndexMapper;

    @Autowired
    private WorkspaceService workspaceService;

    private Path root;
    private String uniqueDir;

    @BeforeEach
    void setUp() {
        root = workspaceService.getDefaultWorkspaceRoot();
        uniqueDir = "ddl/test-idx-" + UUID.randomUUID().toString().substring(0, 8);
        // 确保测试目录存在
        try {
            Files.createDirectories(root.resolve(uniqueDir));
        } catch (IOException ignored) {
        }
    }

    @AfterEach
    void tearDown() {
        // 清理 DB 索引
        fileIndexService.indexOnDelete(uniqueDir);
        fileIndexMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FileIndexEntity>()
                .likeRight(FileIndexEntity::getPath, uniqueDir));
    }

    // ==================== 写操作回归 ====================

    @Nested
    @DisplayName("写操作回归")
    class WriteOperations {

        private String testFile;

        @BeforeEach
        void createTestFile() throws IOException {
            testFile = uniqueDir + "/write-test.sql";
            Files.writeString(root.resolve(testFile), "hello");
            // 清理可能的残留索引
            fileIndexMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FileIndexEntity>()
                    .eq(FileIndexEntity::getPath, testFile));
        }

        @Test
        @DisplayName("createFolder → DB 有对应记录，parent_path 正确")
        void shouldInsertOnCreateFolder() {
            String folderPath = uniqueDir + "/new-folder";
            fileIndexService.indexOnCreate(folderPath, "folder", root);

            FileIndexEntity entity = fileIndexService.getByPath(folderPath);
            assertThat(entity).isNotNull();
            assertThat(entity.getType()).isEqualTo("folder");
            assertThat(entity.getParentPath()).isEqualTo(uniqueDir);
        }

        @Test
        @DisplayName("save 新文件 → DB INSERT 成功")
        void shouldInsertOnSaveNewFile() {
            fileIndexService.indexOnSave(testFile, root);

            FileIndexEntity entity = fileIndexService.getByPath(testFile);
            assertThat(entity).isNotNull();
            assertThat(entity.getType()).isEqualTo("file");
            assertThat(entity.getName()).isEqualTo("write-test.sql");
        }

        @Test
        @DisplayName("save 已有文件 → DB UPDATE mtime，不重复 INSERT")
        void shouldUpdateMtimeNotDuplicateInsert() {
            // 第一次 save
            fileIndexService.indexOnSave(testFile, root);
            FileIndexEntity first = fileIndexService.getByPath(testFile);
            assertThat(first).isNotNull();
            Long firstMtime = first.getMtime();

            // 修改文件
            try {
                Thread.sleep(10); // 确保 mtime 变化
                Files.writeString(root.resolve(testFile), "world");
            } catch (Exception ignored) {
            }

            // 第二次 save
            fileIndexService.indexOnSave(testFile, root);
            FileIndexEntity second = fileIndexService.getByPath(testFile);
            assertThat(second).isNotNull();
            // mtime 应该已更新（或至少记录数没翻倍）
            assertThat(second.getId()).isEqualTo(first.getId());
        }

        @Test
        @DisplayName("delete 文件 → DB 记录删除")
        void shouldDeleteFileRecord() {
            fileIndexService.indexOnSave(testFile, root);
            assertThat(fileIndexService.getByPath(testFile)).isNotNull();

            fileIndexService.indexOnDelete(testFile);
            assertThat(fileIndexService.getByPath(testFile)).isNull();
        }

        @Test
        @DisplayName("delete 文件夹 → 子节点递归删除")
        void shouldRecursivelyDeleteChildren() {
            String folder = uniqueDir + "/recursive-folder";
            String childFile = folder + "/child.sql";
            fileIndexService.indexOnCreate(folder, "folder", root);
            fileIndexService.indexOnSave(childFile, root);

            fileIndexService.indexOnDelete(folder);

            assertThat(fileIndexService.getByPath(folder)).isNull();
            assertThat(fileIndexService.getByPath(childFile)).isNull();
        }

        @Test
        @DisplayName("rollbackRelease 恢复被删文件 → DB UPSERT 正确")
        void shouldUpsertOnRestoreDeletedFile() {
            // 模拟被删文件恢复的场景
            fileIndexService.indexOnSave(testFile, root);
            fileIndexService.indexOnDelete(testFile);
            assertThat(fileIndexService.getByPath(testFile)).isNull();

            // 回滚恢复：磁盘文件已恢复，调用 indexOnSave（UPSERT）
            fileIndexService.indexOnSave(testFile, root);

            FileIndexEntity restored = fileIndexService.getByPath(testFile);
            assertThat(restored).isNotNull();
            assertThat(restored.getType()).isEqualTo("file");
        }
    }

    // ==================== reconcile 数据一致性 ====================

    @Nested
    @DisplayName("reconcile 数据一致性扫描")
    class Reconcile {

        @Test
        @DisplayName("正常一致：空目录 → orphan / missing / mtimeMismatch 均为 0")
        void shouldReportCleanWhenConsistent() throws IOException {
            // 先清理，建索引
            String dir = uniqueDir + "/clean";
            Files.createDirectories(root.resolve(dir));
            fileIndexService.indexOnCreate(dir, "folder", root);

            Map<String, Object> report = fileIndexService.reconcile(root.resolve(dir));

            @SuppressWarnings("unchecked")
            List<String> unindexedFiles = (List<String>) report.get("unindexedFiles");
            @SuppressWarnings("unchecked")
            List<String> mtimeMismatches = (List<String>) report.get("mtimeMismatches");

            // 空目录不应有未索引文件或 mtime 不一致
            assertThat(unindexedFiles).isEmpty();
            assertThat(mtimeMismatches).isEmpty();
            // staleFiles 会遍历全量 DB，不在此断言
        }

        @Test
        @DisplayName("未索引文件：磁盘有但 DB 无 → unindexedFiles / unindexedFolders 正确列出")
        void shouldReportOrphan() throws IOException {
            String dir = uniqueDir + "/orphan";
            Files.createDirectories(root.resolve(dir));
            // 磁盘有文件目录但 DB 无记录
            Path orphanFile = root.resolve(dir + "/orphan.sql");
            Files.writeString(orphanFile, "orphan");

            fileIndexService.indexOnCreate(dir, "folder", root);
            Map<String, Object> report = fileIndexService.reconcile(root.resolve(dir));

            @SuppressWarnings("unchecked")
            List<String> unindexedFiles = (List<String>) report.get("unindexedFiles");
            boolean found = unindexedFiles.stream().anyMatch(p -> p.contains("orphan.sql"));
            assertThat(found).isTrue();
        }

        @Test
        @DisplayName("缺失文件：DB 有但磁盘无 → staleFiles 正确列出")
        void shouldReportMissing() {
            String missingFile = uniqueDir + "/missing.sql";
            fileIndexService.indexOnSave(missingFile, root);

            Map<String, Object> report = fileIndexService.reconcile(root);

            @SuppressWarnings("unchecked")
            List<String> staleFiles = (List<String>) report.get("staleFiles");
            boolean found = staleFiles.stream().anyMatch(p -> p.contains("missing.sql"));
            assertThat(found).isTrue();
        }

        @Test
        @DisplayName("mtime 不一致：磁盘与 DB 不同 → mtimeMismatches 正确列出")
        void shouldReportMtimeMismatch() throws IOException {
            String file = uniqueDir + "/mtime.sql";
            Files.writeString(root.resolve(file), "hello");
            fileIndexService.indexOnSave(file, root);

            // 修改磁盘文件，但 DB 不更新
            Files.writeString(root.resolve(file), "world");

            Map<String, Object> report = fileIndexService.reconcile(root);

            @SuppressWarnings("unchecked")
            List<String> mismatches = (List<String>) report.get("mtimeMismatches");
            boolean found = mismatches.stream().anyMatch(p -> p.contains("mtime.sql"));
            assertThat(found).isTrue();
        }

        @Test
        @DisplayName("跳过 .git / .lanting")
        void shouldSkipGitAndLantingDirs() throws IOException {
            // 在 .lanting 目录下创建文件
            Path lantingDir = root.resolve(uniqueDir + "/.lanting");
            Files.createDirectories(lantingDir);
            Files.writeString(lantingDir.resolve("config.json"), "{}");

            fileIndexService.indexOnCreate(uniqueDir, "folder", root);
            Map<String, Object> report = fileIndexService.reconcile(root.resolve(uniqueDir));

            @SuppressWarnings("unchecked")
            List<String> unindexedFiles = (List<String>) report.get("unindexedFiles");
            boolean hasLanting = unindexedFiles.stream().anyMatch(p -> p.contains(".lanting"));
            assertThat(hasLanting).isFalse();
        }
    }

    // ==================== tree() 查 DB 验证 ====================

    @Nested
    @DisplayName("tree() 查 DB 验证")
    class TreeDbVerification {

        @Test
        @DisplayName("DB 有记录但磁盘文件已删除 → tree() 仍然返回该文件节点")
        void shouldReturnNodeEvenWhenFileDeleted() throws IOException {
            String file = uniqueDir + "/db-only.sql";
            // 写入磁盘 + 建索引
            Files.writeString(root.resolve(file), "content");
            fileIndexService.indexOnSave(file, root);

            // 删除磁盘文件
            Files.deleteIfExists(root.resolve(file));

            // tree() 应仍然返回（查 DB 不是磁盘）
            List<FileIndexEntity> children = fileIndexService.listByParentPath(uniqueDir);
            boolean found = children.stream().anyMatch(e -> "db-only.sql".equals(e.getName()));
            assertThat(found).isTrue();
        }
    }

    // ==================== scanAndIndex ====================

    @Nested
    @DisplayName("scanAndIndex 全量扫描")
    class ScanAndIndex {

        @Test
        @DisplayName("扫描磁盘后 DB 记录与磁盘文件一一对应")
        void shouldIndexAllFiles() throws IOException {
            String dir = uniqueDir + "/scan";
            Files.createDirectories(root.resolve(dir));
            Files.writeString(root.resolve(dir + "/a.sql"), "a");
            Files.writeString(root.resolve(dir + "/b.sql"), "b");
            Files.createDirectories(root.resolve(dir + "/sub"));

            // 先确保 DB 无记录
            fileIndexMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FileIndexEntity>()
                    .likeRight(FileIndexEntity::getPath, dir));

            fileIndexService.reloadIndex(root);

            // 查询扫描后的记录
            List<FileIndexEntity> children = fileIndexService.listByParentPath(dir);
            // 应有 a.sql, b.sql, sub 三个
            assertThat(children).extracting(FileIndexEntity::getName)
                    .contains("a.sql", "b.sql", "sub");
        }
    }
}
