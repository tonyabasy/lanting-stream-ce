package com.lanting.admin.module.publish.Medium;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import static org.mockito.Mockito.mockStatic;

import com.lanting.admin.BaseIntegrationTest;
import com.lanting.admin.common.exception.BusinessException;
import com.lanting.admin.common.util.SecurityUtils;
import com.lanting.admin.module.file.entity.FileIndexEntity;
import com.lanting.admin.module.file.mapper.FileIndexMapper;
import com.lanting.admin.module.file.service.FileLockService;
import com.lanting.admin.module.file.service.WorkspaceService;
import com.lanting.admin.module.publish.entity.PublishEntity;
import com.lanting.admin.module.publish.entity.PublishFileEntity;
import com.lanting.admin.module.publish.mapper.PublishFileMapper;
import com.lanting.admin.module.publish.mapper.PublishMapper;
import com.lanting.admin.module.publish.service.PublishService;
import com.lanting.admin.module.publish.vo.PublishVO;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static com.lanting.admin.module.publish.entity.PublishFileEntity.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * PublishService Medium 测试（SQLite + JGit 本地仓库）。
 * 覆盖 addCommittedList / cancelPublish / publish / diff 主链路。
 */
@DisplayName("PublishService 集成测试（提交 → 撤销 → 发布 → diff）")
class PublishMediumTest extends BaseIntegrationTest {

    @Autowired private PublishService publishService;
    @Autowired private PublishMapper publishMapper;
    @Autowired private PublishFileMapper publishFileMapper;
    @Autowired private WorkspaceService workspaceService;
    @Autowired private FileIndexMapper fileIndexMapper;
    @Autowired private FileLockService fileLockService;

    private Path root;
    private String repoDir;
    private long fileId;
    private String commitHash;

    @BeforeEach
    void setUp() throws Exception {
        root = workspaceService.getDefaultWorkspaceRoot();
        repoDir = "publish-test-" + UUID.randomUUID().toString().substring(0, 8);

        // 1. 在 workspace root 下创建文件并 commit（与 uncommit 共用同一仓库）
        try (Git git = Git.open(root.toFile())) {
            Path dir = root.resolve(repoDir);
            Files.createDirectories(dir);
            Path file = dir.resolve("test.sql");
            Files.writeString(file, "SELECT 1;", StandardCharsets.UTF_8);
            git.add().addFilepattern(repoDir + "/test.sql").call();
            commitHash = git.commit().setMessage("init").call().getName();
        }

        // 2. 注册 FileIndex
        FileIndexEntity idx = new FileIndexEntity();
        idx.setName("test.sql");
        idx.setPath(repoDir + "/test.sql");
        idx.setParentPath(repoDir);
        idx.setType(FileIndexEntity.FILE);
        idx.setLatestCommitHash(commitHash);
        idx.setDeletedAt(0L);
        idx.setCreateTime(System.currentTimeMillis());
        idx.setUpdateTime(System.currentTimeMillis());
        fileIndexMapper.insert(idx);
        fileId = idx.getId();

        // 3. 上锁（GitFileService.commit 要求文件已上锁）
        fileLockService.acquire(fileId, "user1");

        // 4. 清空发布数据
        publishFileMapper.delete(new LambdaQueryWrapper<>());
        publishMapper.delete(new LambdaQueryWrapper<>());
    }

    // ==================== addCommittedList ====================

    @Test
    @DisplayName("有变更提交 → COMMITTED 行写入")
    void commitWithChanges_entersPool() {
        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::currentUser).thenReturn("user1");

            // 文件在 git 中，但磁盘无变更（刚提交过）。
            // uncommit 返回空 → 应抛异常，不走入池。
            // 需要先修改文件产生变更。
            Path file = root.resolve(repoDir).resolve("test.sql");
            try { Files.writeString(file, "SELECT 2;"); } catch (Exception ignored) {}

            publishService.addCommittedList(List.of(fileId), "test commit");

            PublishFileEntity pf = publishFileMapper.selectOne(
                    new LambdaQueryWrapper<PublishFileEntity>()
                            .eq(PublishFileEntity::getFileId, fileId)
                            .eq(PublishFileEntity::getStatus, STATUS_COMMITTED));
            assertNotNull(pf, "应有一条 COMMITTED 记录");
            assertEquals("user1", pf.getCreatedBy());
        }
    }

    @Test
    @DisplayName("无变更提交 → 抛 NOTHING_TO_COMMIT")
    void commitWithoutChanges_throwsException() {
        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::currentUser).thenReturn("user1");

            // 刚 commit 完，内容无变更 → uncommit 返回空
            assertThrows(BusinessException.class, () ->
                    publishService.addCommittedList(List.of(fileId), "msg"));
        }
    }

    @Test
    @DisplayName("已在池中再提交 → 不新增行，更新时间")
    void alreadyInPool_updatesTimestamp() throws Exception {
        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::currentUser).thenReturn("user1");

            // 先入池
            Path file = root.resolve(repoDir).resolve("test.sql");
            Files.writeString(file, "SELECT 2;");
            publishService.addCommittedList(List.of(fileId), "first");
            long firstTime = publishFileMapper.selectOne(
                    new LambdaQueryWrapper<PublishFileEntity>()
                            .eq(PublishFileEntity::getFileId, fileId)
                            .eq(PublishFileEntity::getStatus, STATUS_COMMITTED))
                    .getUpdateTime();

            // 再修改 + 再提交
            Files.writeString(file, "SELECT 3;");
            publishService.addCommittedList(List.of(fileId), "second");

            // 仍是 1 条 COMMITTED
            long count = publishFileMapper.selectCount(
                    new LambdaQueryWrapper<PublishFileEntity>()
                            .eq(PublishFileEntity::getFileId, fileId)
                            .eq(PublishFileEntity::getStatus, STATUS_COMMITTED));
            assertEquals(1, count, "仍应只有 1 条 COMMITTED");

            long secondTime = publishFileMapper.selectOne(
                    new LambdaQueryWrapper<PublishFileEntity>()
                            .eq(PublishFileEntity::getFileId, fileId)
                            .eq(PublishFileEntity::getStatus, STATUS_COMMITTED))
                    .getUpdateTime();
            assertTrue(secondTime >= firstTime, "updateTime 已刷新");
        }
    }

    // ==================== cancelPublish ====================

    @Test
    @DisplayName("撤销 → COMMITTED 行翻 CANCELED")
    void cancel_removesFromPool() throws Exception {
        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::currentUser).thenReturn("user1");

            Files.writeString(root.resolve(repoDir).resolve("test.sql"), "SELECT 2;");
            publishService.addCommittedList(List.of(fileId), "msg");
            publishService.cancelPublish(List.of(fileId));

            PublishFileEntity canceled = publishFileMapper.selectOne(
                    new LambdaQueryWrapper<PublishFileEntity>()
                            .eq(PublishFileEntity::getFileId, fileId)
                            .eq(PublishFileEntity::getStatus, STATUS_CANCELED));
            assertNotNull(canceled, "应有一条 CANCELED 记录");
        }
    }

    // ==================== publish ====================

    @Test
    @DisplayName("发布快照：COMMITTED → PUBLISHED，commitHash 定格")
    void publish_snapshotsCommitHash() throws Exception {
        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::currentUser).thenReturn("user1");

            // 入池
            Files.writeString(root.resolve(repoDir).resolve("test.sql"), "SELECT 2;");
            publishService.addCommittedList(List.of(fileId), "msg");

            // 发布
            PublishVO result = publishService.publish(List.of(fileId), "Pub1");

            assertNotNull(result.getPublishId());
            assertEquals("Pub1", result.getDisplayName());
            assertThat(result.getFiles()).hasSize(1);
            assertNotNull(result.getFiles().get(0).getCommitHash(),
                    "发布应定格 commitHash");

            // DB 验证
            PublishFileEntity pf = publishFileMapper.selectOne(
                    new LambdaQueryWrapper<PublishFileEntity>()
                            .eq(PublishFileEntity::getFileId, fileId)
                            .eq(PublishFileEntity::getStatus, STATUS_PUBLISHED));
            assertNotNull(pf);
            assertEquals(result.getPublishId(), pf.getPublishId());
        }
    }

    @Test
    @DisplayName("未入池文件发布 → 抛 FILE_NOT_COMMITTED")
    void publish_notCommitted_throwsException() {
        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::currentUser).thenReturn("user1");

            // fileId 有 commitHash 但不在池中
            assertThrows(BusinessException.class, () ->
                    publishService.publish(List.of(fileId), "Pub1"));
        }
    }

    // ==================== diff ====================

    @Test
    @DisplayName("从未发布过 → diff 左侧为空树，返回 unified diff")
    void diff_unpublished_vsEmptyTree() throws Exception {
        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::currentUser).thenReturn("user1");

            Files.writeString(root.resolve(repoDir).resolve("test.sql"), "SELECT 2;");
            publishService.addCommittedList(List.of(fileId), "msg");

            String diff = publishService.diff(fileId);

            assertNotNull(diff);
            assertThat(diff).isNotEmpty();
            assertThat(diff).contains("SELECT 2;");
        }
    }
}
