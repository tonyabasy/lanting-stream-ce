package com.lanting.admin.module.file.service;

import com.lanting.admin.BaseIntegrationTest;
import com.lanting.admin.common.util.SecurityUtils;
import com.lanting.admin.module.file.dto.CreateFileDTO;
import com.lanting.admin.module.file.dto.CreateFolderDTO;
import com.lanting.admin.module.file.entity.FileIndexEntity;
import com.lanting.admin.module.file.mapper.FileIndexMapper;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import java.util.stream.Collectors;

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
    @Autowired
    private GitFileService gitFileService;

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
        fileIndexService.deletePhysicallyByPathRecursively(uniqueDir);
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
    }

    // ==================== 批量索引操作 ====================

    @Nested
    @DisplayName("批量索引操作")
    class BatchIndexOperations {
        private MockedStatic<SecurityUtils> securityUtilsMock;

        @BeforeEach
        void mockCurrentUser() {
            securityUtilsMock = Mockito.mockStatic(SecurityUtils.class);
            securityUtilsMock.when(SecurityUtils::currentUser).thenReturn("admin");
        }

        @AfterEach
        void closeMock() {
            if (securityUtilsMock != null) {
                securityUtilsMock.close();
            }
        }

        @Test
        @DisplayName("indexOnDelete 删除 3 层目录及 10 个文件")
        void shouldDeleteAllByPathPrefix() {
            // 3 层目录：uniqueDir/l1/l2
            String l1 = uniqueDir + "/l1";
            String l2 = l1 + "/l2";
            fileIndexService.indexOnCreate(l1, "folder", root);
            fileIndexService.indexOnCreate(l2, "folder", root);

            // 10 个文件分布在各层级
            String[] files = {
                    uniqueDir + "/f1.sql",
                    uniqueDir + "/f2.sql",
                    l1 + "/f3.sql",
                    l1 + "/f4.sql",
                    l1 + "/f5.sql",
                    l2 + "/f6.sql",
                    l2 + "/f7.sql",
                    l2 + "/f8.sql",
                    l2 + "/f9.sql",
                    l2 + "/f10.sql"
            };
            for (String file : files) {
                fileIndexService.indexOnSave(file, root);
            }

            // 通过前缀删除整个目录树
            fileIndexService.deletePhysicallyByPathRecursively(uniqueDir);

            // 验证所有文件和目录记录都被删除
            assertThat(fileIndexService.getByPath(uniqueDir)).isNull();
            assertThat(fileIndexService.getByPath(l1)).isNull();
            assertThat(fileIndexService.getByPath(l2)).isNull();
            for (String file : files) {
                assertThat(fileIndexService.getByPath(file)).isNull();
            }
        }

        @Test
        @DisplayName("indexOnDeleteByIds 只删除指定文件 ID，保留目录")
        void shouldDeleteOnlySpecifiedFileIds() {
            String folder = uniqueDir + "/folder";
            fileIndexService.indexOnCreate(folder, "folder", root);

            // 4 个文件
            String[] files = {
                    folder + "/a.sql",
                    folder + "/b.sql",
                    folder + "/c.sql",
                    folder + "/d.sql"
            };
            Set<Long> fileIds = new HashSet<>();
            for (String file : files) {
                fileIndexService.indexOnSave(file, root);
                FileIndexEntity entity = fileIndexService.getByPath(file);
                assertThat(entity).isNotNull();
                fileIds.add(entity.getId());
            }

            // 通过文件 ID 删除
            fileIndexService.deletePhysicallyByIds(fileIds);

            // 验证文件被删除，目录保留
            for (String file : files) {
                assertThat(fileIndexService.getByPath(file)).isNull();
            }
            assertThat(fileIndexService.getByPath(folder)).isNotNull();
        }

        @Test
        @DisplayName("listAllChildren 按前缀查找 4 层目录下第二层级的所有文件")
        void shouldListAllChildrenByPrefix() {
            // 4 层目录：uniqueDir/l1/l2/l3
            String l1 = uniqueDir + "/l1";
            String l2 = l1 + "/l2";
            String l3 = l2 + "/l3";

            gitFileService.createFolder(new CreateFolderDTO(l1));
            gitFileService.createFolder(new CreateFolderDTO(l2));
            gitFileService.createFolder(new CreateFolderDTO(l3));

            // 10 个文件
            String[] files = {
                    l1 + "/f1.sql",
                    l1 + "/f2.sql",
                    l2 + "/f3.sql",
                    l2 + "/f4.sql",
                    l2 + "/f5.sql",
                    l3 + "/f6.sql",
                    l3 + "/f7.sql",
                    l3 + "/f8.sql",
                    l3 + "/f9.sql",
                    l3 + "/f10.sql"
            };
            for (String file : files) {
                gitFileService.create(new CreateFileDTO(file));
            }

            // 根据前缀 l1 查找所有 children，不包括 l1 目录本身
            List<FileIndexEntity> children = fileIndexService.listAllChildren(l1);

            Set<String> childPaths = children.stream()
                    .map(FileIndexEntity::getPath)
                    .collect(Collectors.toSet());

            // 应包含 l2、l3 目录及所有子孙文件
            assertThat(childPaths).contains(l2, l3);
            for (String file : files) {
                assertThat(childPaths).contains(file);
            }
            // 不包含 l1 自身
            assertThat(childPaths).doesNotContain(l1);
            // 共 11 条：l2 目录 + l3 目录 + 10 个文件
            assertThat(children).hasSize(12);
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
            List<FileIndexEntity> children = fileIndexService.listDirectlyChildren(uniqueDir);
            boolean found = children.stream().anyMatch(e -> "db-only.sql".equals(e.getName()));
            assertThat(found).isTrue();
        }
    }
}
