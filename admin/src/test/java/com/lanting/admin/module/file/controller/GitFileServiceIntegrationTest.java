package com.lanting.admin.module.file.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.lanting.admin.BaseIntegrationTest;
import com.lanting.admin.common.util.JACKSON;
import com.lanting.admin.module.file.dto.*;
import com.lanting.admin.module.file.entity.FileIndexEntity;
import com.lanting.admin.module.file.result.FileResultCode;
import com.lanting.admin.module.file.service.FileIndexService;
import com.lanting.admin.module.file.service.WorkspaceService;
import com.lanting.admin.module.file.vo.CommitResultVO;
import com.lanting.admin.module.file.vo.PublishVO;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GitFileService 主链路集成测试。通过 HTTP 调用覆盖完整文件操作流程。
 * <p>
 * 注：原设计为服务集成测试，但 GitFileService 内部通过 Sa-Token 获取当前用户名，
 * 直接调用 service 方法需要搭建 Sa-Token mock 上下文。为保持测试稳定性，统一走 HTTP 链路。
 *
 * @author wangzhao
 */
@DisplayName("GitFileService 主链路集成测试")
class GitFileServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private WorkspaceService workspaceService;
    @Autowired
    private FileIndexService fileIndexService;

    private String token;
    private String uniquePath;

    @BeforeEach
    void setUp() {
        token = loginAsAdmin();
        uniquePath = "sql/test-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @AfterEach
    void tearDown() {
        // HTTP 链路无需手动清理锁
    }

    private Long fileIdByPath(String path) {
        var entity = fileIndexService.getByPath(path);
        return entity == null ? null : entity.getId();
    }

    // ==================== 严重 bug 回归：history addPath ====================

    @Nested
    @DisplayName("严重 bug 回归：history 指定 path 只返回该文件的 commit")
    class HistoryPathRegression {

        @Test
        @DisplayName("只提交文件 A 时，查询文件 B 的历史应为空")
        void historyShouldFilterByPath() {
            createFolder(uniquePath);
            String fileA = uniquePath + "/A.sql";
            String fileB = uniquePath + "/B.sql";

            // 只提交 A
            Long fileIdA = createFile(fileA);
            acquireLock(fileIdA);
            saveFile(fileIdA, "A");
            commit(List.of(fileIdA), "add A");

            // 查询 B 的历史，应为空
            Long fileIdB = createFile(fileB);
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    "/api/files/history?fileId=" + fileIdB + "&pageNum=1&pageSize=10",
                    HttpMethod.GET,
                    new HttpEntity<>(authHeaders(token)),
                    JsonNode.class);
            assertThat(Objects.requireNonNull(response.getBody()).path("code").asInt()).isEqualTo(0);
            JsonNode data = Objects.requireNonNull(response.getBody()).path("data");
            assertThat(data.path("records").size()).isEqualTo(0);
            assertThat(data.path("hasMore").asBoolean()).isFalse();
        }

        @Test
        @DisplayName("分别提交 A 和 B，查询 A 的历史只包含 A 的 commit")
        void historyShouldOnlyContainCommitsForSpecifiedPath() {
            createFolder(uniquePath);
            String fileA = uniquePath + "/filter-A.sql";
            String fileB = uniquePath + "/filter-B.sql";

            Long fileIdA = createFile(fileA);
            acquireLock(fileIdA);
            saveFile(fileIdA, "A");
            commit(List.of(fileIdA), "add A");

            Long fileIdB = createFile(fileB);
            acquireLock(fileIdB);
            saveFile(fileIdB, "B");
            commit(List.of(fileIdB), "add B");

            // 查询 A 的历史，只应有 1 条
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    "/api/files/history?fileId=" + fileIdA + "&pageNum=1&pageSize=10",
                    HttpMethod.GET,
                    new HttpEntity<>(authHeaders(token)),
                    JsonNode.class);
            assertThat(Objects.requireNonNull(response.getBody()).path("data").path("records").size()).isEqualTo(1);
            assertThat(Objects.requireNonNull(response.getBody()).path("data").path("hasMore").asBoolean()).isFalse();
            assertThat(Objects.requireNonNull(response.getBody()).path("data").path("records")
                    .get(0).path("message").asText()).contains("add A");
        }

        @Test
        @DisplayName("超过 pageSize 的提交返回 hasMore=true")
        void historyShouldReturnHasMoreWhenMoreCommitsExist() {
            createFolder(uniquePath);
            String file = uniquePath + "/paging.sql";
            Long fileId = createFile(file);

            acquireLock(fileId);
            saveFile(fileId, "v1");
            commit(List.of(fileId), "v1");
            saveFile(fileId, "v2");
            commit(List.of(fileId), "v2");
            saveFile(fileId, "v3");
            commit(List.of(fileId), "v3");

            // pageSize=2，应返回 2 条记录且 hasMore=true
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    "/api/files/history?fileId=" + fileId + "&pageNum=1&pageSize=2",
                    HttpMethod.GET,
                    new HttpEntity<>(authHeaders(token)),
                    JsonNode.class);
            assertThat(Objects.requireNonNull(response.getBody()).path("code").asInt()).isEqualTo(0);
            assertThat(Objects.requireNonNull(response.getBody()).path("data").path("records").size()).isEqualTo(2);
            assertThat(Objects.requireNonNull(response.getBody()).path("data").path("hasMore").asBoolean()).isTrue();
        }
    }

    // ==================== createFolder 已存在路径 ====================

    @Nested
    @DisplayName("createFolder 已存在路径")
    class CreateFolderExisting {

        @Test
        @DisplayName("创建已存在的文件夹 → FILE_ALREADY_EXISTS（30707）")
        void shouldReturnAlreadyExistsWhenFolderExists() {
            createFolder(uniquePath);

            // 再次创建同一路径
            CreateFolderDTO dto = new CreateFolderDTO();
            dto.setPath(uniquePath);
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    "/api/files/folder",
                    HttpMethod.POST,
                    new HttpEntity<>(dto, authHeaders(token)),
                    JsonNode.class);
            assertThat(Objects.requireNonNull(response.getBody()).path("code").asInt()).isEqualTo(30707);
        }
    }

    // ==================== 4.2 path="." / "./" ====================

    @Nested
    @DisplayName("路径安全：. 和 ./ 路径")
    class DotPathSecurity {

        @Test
        @DisplayName("path = '.' → PATH_ILLEGAL（30705）")
        void shouldRejectDotPath() {
            CreateFileDTO dto = new CreateFileDTO();
            dto.setPath(".");
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    "/api/files/create", HttpMethod.POST,
                    new HttpEntity<>(dto, authHeaders(token)), JsonNode.class);
            assertThat(Objects.requireNonNull(response.getBody()).path("code").asInt()).isEqualTo(30705);
        }

        @Test
        @DisplayName("path = './' → PATH_ILLEGAL（30705）")
        void shouldRejectDotSlashPath() {
            CreateFileDTO dto = new CreateFileDTO();
            dto.setPath("./");
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    "/api/files/create", HttpMethod.POST,
                    new HttpEntity<>(dto, authHeaders(token)), JsonNode.class);
            assertThat(Objects.requireNonNull(response.getBody()).path("code").asInt()).isEqualTo(30705);
        }
    }

    // ==================== 4.3 Git 写操作边界 ====================

    @Nested
    @DisplayName("4.3 Git 写操作边界")
    class GitWriteEdgeCases {

        @Test
        @DisplayName("删除文件时未持锁 → FILE_LOCKED（30709）")
        void deleteWithoutLockShouldReturn30709() {
            createFolder(uniquePath);
            String filePath = uniquePath + "/unlocked.sql";
            Long fileId = createFile(filePath);

            acquireLock(fileId);
            saveFile(fileId, "unlocked");
            // 抢锁修改后释放锁，当前用户已经不再持有锁了
            releaseLock(fileId);

            // 不抢锁，直接尝试删除
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    "/api/files?fileId=" + fileId,
                    HttpMethod.DELETE,
                    new HttpEntity<>(authHeaders(token)),
                    JsonNode.class);
            assertThat(Objects.requireNonNull(response.getBody()).path("code").asInt()).isEqualTo(30709);
        }

        /**
         * 提交文件没抢锁
         */
        @Test
        @DisplayName("提交文件时没抢锁")
        void commitAllLockedByOthersShouldReturn30713() {
            createFolder(uniquePath);
            String filePath = uniquePath + "/other.sql";

            // admin 抢锁
            Long fileId = createFile(filePath);
            acquireLock(fileId);
            saveFile(fileId, "content");

            // 用另一个用户登录尝试提交 admin 持有锁的文件
            String anotherUser = "test-user-1";
            createAnotherUser(anotherUser);
            String anotherUserToken = login(anotherUser, anotherUser);

            CommitFileDTO dto = new CommitFileDTO();
            dto.setFileIds(List.of(fileId)); // admin 锁定文件
            dto.setMessage("should fail");
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    "/api/files/commit", HttpMethod.POST,
                    new HttpEntity<>(dto, authHeaders(anotherUserToken)), JsonNode.class);
            assertThat(Objects.requireNonNull(response.getBody()).path("code").asInt()).isEqualTo(FileResultCode.FILE_LOCKED.getCode());
        }

        @Test
        @DisplayName("非法 SHA 调用 revert → 400 + PARAM_INVALID（10001）")
        void revertWithInvalidShaShouldReturn400() {
            createFolder(uniquePath);
            String filePath = uniquePath + "/revert-sha.sql";
            Long fileId = createFile(filePath);
            acquireLock(fileId);
            saveFile(fileId, "content");
            commit(List.of(fileId), "add");

            RevertFileDTO dto = new RevertFileDTO();
            dto.setFileId(fileId);
            dto.setCommitHash("not-a-valid-sha");
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    "/api/files/revert", HttpMethod.POST,
                    new HttpEntity<>(dto, authHeaders(token)), JsonNode.class);
            assertThat(response.getStatusCode().value()).isEqualTo(400);
            assertThat(Objects.requireNonNull(response.getBody()).path("code").asInt()).isEqualTo(10001);
        }
    }

    // ==================== 4.4 发布与回滚边界 ====================

    @Nested
    @DisplayName("4.4 发布与回滚边界")
    class PublishAndRollbackEdgeCases {

        @Test
        @DisplayName("发布时磁盘有未提交变更，tag 不含未提交内容")
        void publishShouldNotIncludeUncommittedChanges() throws Exception {
            createFolder(uniquePath);
            String filePath = uniquePath + "/uncommitted.sql";
            Long fileId = createFile(filePath);
            acquireLock(fileId);

            // 先提交一个基准版本
            saveFile(fileId, "committed baseline");
            commit(List.of(fileId), "committed baseline");
            // 保存到磁盘但不 commit
            saveFile(fileId, "uncommitted content");

            // 发布
            PublishVO pub = publish("should not include uncommitted");

            // 验证 tag 的 tree 中不含该文件（未 commit，不应进入 tag）
            Path root = workspaceService.getDefaultWorkspaceRoot();
            // 不验证文件是否存在，而是验证 tag 里的内容是已提交版本
            try (Git git = Git.open(root.toFile());
                 RevWalk walk = new RevWalk(git.getRepository())) {
                ObjectId tagCommit = git.getRepository()
                        .resolve(Constants.R_TAGS + pub.getTagName());
                RevCommit commit = walk.parseCommit(tagCommit);
                try (TreeWalk treeWalk = new TreeWalk(git.getRepository())) {
                    treeWalk.addTree(commit.getTree());
                    treeWalk.setRecursive(true);
                    treeWalk.setFilter(PathFilter.create(filePath));
                    assertThat(treeWalk.next()).isTrue();
                    ObjectLoader loader = git.getRepository().open(treeWalk.getObjectId(0));
                    String contentInTag = new String(loader.getBytes(), StandardCharsets.UTF_8);
                    // tag 里的内容是已提交的版本，不是磁盘上未提交的内容
                    assertThat(contentInTag).isEqualTo("committed baseline");
                    assertThat(contentInTag).isNotEqualTo("uncommitted content");
                }
            }
        }

        @Test
        @DisplayName("同一 HEAD 连续发布两次，生成两个不同 tag，都指向同一 commit")
        void twoPublishesOnSameHeadShouldGenerateDifferentTags() throws Exception {
            createFolder(uniquePath);
            String filePath = uniquePath + "/base.sql";
            Long fileId = createFile(filePath);
            acquireLock(fileId);

            // 先提交一个基准版本
            saveFile(fileId, "committed baseline");
            commit(List.of(fileId), "committed baseline");
            // 第一次发布
            PublishVO pub1 = publish("first");
            Thread.sleep(1000);
            // 第二次发布（同一 HEAD）
            PublishVO pub2 = publish("second");

            assertThat(pub1.getTagName()).isNotEqualTo(pub2.getTagName());
            assertThat(pub1.getCommitHash()).isEqualTo(pub2.getCommitHash());
        }
    }

    // ==================== 重命名文件夹 ====================

    @Nested
    @DisplayName("重命名文件夹")
    class RenameFolder {

        @Test
        @DisplayName("rename 文件夹后，3 个文件和 1 个子目录的索引与磁盘路径同步更新")
        void shouldRenameFolderAndUpdateChildrenPaths() throws Exception {
            String folderPath = uniquePath + "/folder";
            createFolder(uniquePath);
            createFolder(folderPath);

            // 3 个文件
            String fileA = folderPath + "/a.sql";
            String fileB = folderPath + "/b.sql";
            String fileC = folderPath + "/c.sql";
            Long fileIdA = createFile(fileA);
            Long fileIdB = createFile(fileB);
            Long fileIdC = createFile(fileC);

            // 1 个子目录 + 1 个文件
            String subFolder = folderPath + "/sub";
            createFolder(subFolder);
            String subFile = subFolder + "/sub.sql";
            Long subFileId = createFile(subFile);

            Long folderId = fileIdByPath(folderPath);
            String newName = "renamed-folder";
            String newFolderPath = uniquePath + "/" + newName;

            rename(folderId, newName);

            Path root = workspaceService.getDefaultWorkspaceRoot();

            // 磁盘旧路径不存在
            assertThat(Files.exists(root.resolve(folderPath))).isFalse();
            assertThat(Files.exists(root.resolve(fileA))).isFalse();
            assertThat(Files.exists(root.resolve(fileB))).isFalse();
            assertThat(Files.exists(root.resolve(fileC))).isFalse();
            assertThat(Files.exists(root.resolve(subFolder))).isFalse();
            assertThat(Files.exists(root.resolve(subFile))).isFalse();

            // 磁盘新路径存在
            assertThat(Files.exists(root.resolve(newFolderPath))).isTrue();
            assertThat(Files.exists(root.resolve(newFolderPath + "/a.sql"))).isTrue();
            assertThat(Files.exists(root.resolve(newFolderPath + "/b.sql"))).isTrue();
            assertThat(Files.exists(root.resolve(newFolderPath + "/c.sql"))).isTrue();
            assertThat(Files.exists(root.resolve(newFolderPath + "/sub"))).isTrue();
            assertThat(Files.exists(root.resolve(newFolderPath + "/sub/sub.sql"))).isTrue();

            // 索引旧路径不存在
            assertThat(fileIndexService.getByPath(folderPath)).isNull();
            assertThat(fileIndexService.getByPath(fileA)).isNull();
            assertThat(fileIndexService.getByPath(fileB)).isNull();
            assertThat(fileIndexService.getByPath(fileC)).isNull();
            assertThat(fileIndexService.getByPath(subFolder)).isNull();
            assertThat(fileIndexService.getByPath(subFile)).isNull();

            // 索引新路径正确
            FileIndexEntity newFolder = fileIndexService.getByPath(newFolderPath);
            assertThat(newFolder).isNotNull();
            assertThat(newFolder.getParentPath()).isEqualTo(uniquePath);

            FileIndexEntity newA = fileIndexService.getByPath(newFolderPath + "/a.sql");
            FileIndexEntity newB = fileIndexService.getByPath(newFolderPath + "/b.sql");
            FileIndexEntity newC = fileIndexService.getByPath(newFolderPath + "/c.sql");
            FileIndexEntity newSubFolder = fileIndexService.getByPath(newFolderPath + "/sub");
            FileIndexEntity newSubFile = fileIndexService.getByPath(newFolderPath + "/sub/sub.sql");

            assertThat(newA).isNotNull();
            assertThat(newA.getParentPath()).isEqualTo(newFolderPath);
            assertThat(newB).isNotNull();
            assertThat(newB.getParentPath()).isEqualTo(newFolderPath);
            assertThat(newC).isNotNull();
            assertThat(newC.getParentPath()).isEqualTo(newFolderPath);
            assertThat(newSubFolder).isNotNull();
            assertThat(newSubFolder.getParentPath()).isEqualTo(newFolderPath);
            assertThat(newSubFile).isNotNull();
            assertThat(newSubFile.getParentPath()).isEqualTo(newFolderPath + "/sub");

            // ID 保持不变
            assertThat(newA.getId()).isEqualTo(fileIdA);
            assertThat(newB.getId()).isEqualTo(fileIdB);
            assertThat(newC.getId()).isEqualTo(fileIdC);
            assertThat(newSubFile.getId()).isEqualTo(subFileId);
        }
    }

    // ==================== 完整正向流程 ====================

    @Nested
    @DisplayName("完整正向流程")
    class FullFlow {

        @Test
        @DisplayName("创建目录 → 创建文件 → 抢锁 → 保存文件 → 查看文件树 → 读取内容 → 提交文件 → 发布")
        void shouldCompleteFullFlow() {
            createFolder(uniquePath);
            String filePath = uniquePath + "/test.sql";
            Long fileId = createFile(filePath);
            acquireLock(fileId);
            saveFile(fileId, "SELECT 1");

            // 查看文件树
            ResponseEntity<JsonNode> treeResponse = restTemplate.exchange(
                    "/api/files/tree?parentPath=" + uniquePath,
                    HttpMethod.GET, new HttpEntity<>(authHeaders(token)), JsonNode.class);
            JsonNode tree = Objects.requireNonNull(treeResponse.getBody()).path("data");
            assertThat(tree).hasSize(1);
            assertThat(tree.get(0).path("name").asText()).isEqualTo("test.sql");
            assertThat(tree.get(0).path("type").asText()).isEqualTo("file");
            assertThat(tree.get(0).path("lockedBy").asText()).isEqualTo("1");

            // 读取内容
            ResponseEntity<JsonNode> contentResponse = restTemplate.exchange(
                    "/api/files/content?fileId=" + fileId,
                    HttpMethod.GET, new HttpEntity<>(authHeaders(token)), JsonNode.class);
            assertThat(Objects.requireNonNull(contentResponse.getBody()).path("data").asText()).isEqualTo("SELECT 1");

            // 提交
            commit(List.of(fileId), "add test.sql");

            // 发布
            PublishVO publish = publish("test release");
            assertThat(publish.getTagName()).startsWith("release-");
            assertThat(publish.getCommitHash()).isNotBlank();
        }
    }

    // ==================== revert 文件级回滚 ====================

    @Nested
    @DisplayName("revert 文件级回滚")
    class Revert {

        @Test
        @DisplayName("回滚到指定 commit 后磁盘内容恢复")
        void shouldRevertToCommit() {
            createFolder(uniquePath);
            String filePath = uniquePath + "/revert.sql";
            Long fileId = createFile(filePath);

            acquireLock(fileId);
            saveFile(fileId, "v1");
            commit(List.of(fileId), "v1");
            String hashV1 = lastCommitHash(fileId);

            saveFile(fileId, "v2");
            commit(List.of(fileId), "v2");

            RevertFileDTO dto = new RevertFileDTO();
            dto.setFileId(fileId);
            dto.setCommitHash(hashV1);
            ResponseEntity<JsonNode> revertResponse = restTemplate.exchange(
                    "/api/files/revert",
                    HttpMethod.POST, new HttpEntity<>(dto, authHeaders(token)), JsonNode.class);
            assertThat(Objects.requireNonNull(revertResponse.getBody()).path("code").asInt()).isEqualTo(0);

            ResponseEntity<JsonNode> contentResponse = restTemplate.exchange(
                    "/api/files/content?fileId=" + fileId,
                    HttpMethod.GET, new HttpEntity<>(authHeaders(token)), JsonNode.class);
            assertThat(Objects.requireNonNull(contentResponse.getBody()).path("data").asText()).isEqualTo("v1");
        }

        @Test
        @DisplayName("commit 15 次后回滚到第 10 次提交")
        void shouldRevertTo21stCommitAmong50() {
            createFolder(uniquePath);
            String filePath = uniquePath + "/history-50.sql";
            Long fileId = createFile(filePath);
            acquireLock(fileId);

            for (int i = 1; i <= 15; i++) {
                saveFile(fileId, "v" + i);
                commit(List.of(fileId), "v" + i);
            }

            // 查询历史，取第 11 条（index 10）作为回滚目标
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    "/api/files/history?fileId=" + fileId + "&pageNum=1&pageSize=50",
                    HttpMethod.GET, new HttpEntity<>(authHeaders(token)), JsonNode.class);
            assertThat(Objects.requireNonNull(response.getBody()).path("code").asInt()).isEqualTo(0);
            JsonNode records = Objects.requireNonNull(response.getBody()).path("data").path("records");
            assertThat(records.size()).isGreaterThanOrEqualTo(11);
            String targetHash = records.get(10).path("commitHash").asText();

            RevertFileDTO dto = new RevertFileDTO();
            dto.setFileId(fileId);
            dto.setCommitHash(targetHash);
            ResponseEntity<JsonNode> revertResponse = restTemplate.exchange(
                    "/api/files/revert", HttpMethod.POST,
                    new HttpEntity<>(dto, authHeaders(token)), JsonNode.class);
            assertThat(Objects.requireNonNull(revertResponse.getBody()).path("code").asInt()).isEqualTo(0);

            // 第 11 条记录对应 v5（历史为倒序：v15, v14, ..., v1）
            assertThat(content(fileId)).isEqualTo("v5");
        }

        @Test
        @DisplayName("revert 只恢复工作区内容，不产生新 commit")
        void shouldRevertWithoutCreatingNewCommit() {
            // 1. 用户 A 创建 f1，提交 c1，发布
            String userA = "user-a-revert";
            createAnotherUser(userA);
            String tokenA = login(userA, userA);

            createFolder(uniquePath);
            String filePath = uniquePath + "/f1.sql";
            Long fileId = createFile(filePath);
            acquireLock(fileId, tokenA);
            saveFile(fileId, "A-content", tokenA);
            commit(List.of(fileId), "c1 by A", tokenA);
            PublishVO publishA = publish("A release", tokenA);
            String hashC1 = publishA.getCommitHash();

            // 2. 用户 B 修改 f1，提交 c2
            String userB = "user-b-revert";
            createAnotherUser(userB);
            String tokenB = login(userB, userB);

            acquireLock(fileId, tokenB);
            saveFile(fileId, "B-content", tokenB);
            commit(List.of(fileId), "c2 by B", tokenB);
            String hashC2BeforeRevert = lastCommitHash(fileId, tokenB);

            // 3. B 再次修改后发现改坏，回滚到 A 的 c1
            saveFile(fileId, "B-bad-content", tokenB);

            RevertFileDTO dto = new RevertFileDTO();
            dto.setFileId(fileId);
            dto.setCommitHash(hashC1);
            ResponseEntity<JsonNode> revertResponse = restTemplate.exchange(
                    "/api/files/revert", HttpMethod.POST,
                    new HttpEntity<>(dto, authHeaders(tokenB)), JsonNode.class);
            assertThat(Objects.requireNonNull(revertResponse.getBody()).path("code").asInt()).isEqualTo(0);

            // 4. 验证：工作区内容恢复为 A 的内容，HEAD 仍然是 c2
            assertThat(content(fileId, tokenB)).as("working content should be A's version").isEqualTo("A-content");
            String hashC2AfterRevert = lastCommitHash(fileId, tokenB);
            assertThat(hashC2AfterRevert).as("HEAD should remain c2, no new commit created").isEqualTo(hashC2BeforeRevert);
        }
    }

    // ==================== scan/reconcile 一致性校验 ====================

    @Nested
    @DisplayName("scan/reconcile 一致性校验")
    class ScanAndReconcile {

        @Test
        @DisplayName("reconcile 发现 CRC32/缺失/未索引不一致，repair(disk wins) 后全一致")
        void shouldDetectMismatchesAndRepairWithDiskWinning() throws Exception {
            createFolder(uniquePath);

            // 1. 在 DB 与 disk 中创建 15 个一致文件
            List<String> consistentFiles = new ArrayList<>();
            List<Long> consistentFileIds = new ArrayList<>();
            for (int i = 1; i <= 15; i++) {
                String path = uniquePath + "/c" + i + ".sql";
                Long fileId = createFile(path);
                acquireLock(fileId);
                saveFile(fileId, "consistent-" + i);
                consistentFiles.add(path);
                consistentFileIds.add(fileId);
            }

            // 2. 对 c1-c3 直接修改磁盘内容，但把 mtime 还原，制造 CRC32 不一致
            List<String> checksumMismatched = new ArrayList<>();
            for (int i = 1; i <= 3; i++) {
                String path = consistentFiles.get(i - 1);
                Path diskPath = workspaceService.getDefaultWorkspaceRoot().resolve(path);
                long originalMtime = Files.getLastModifiedTime(diskPath).toMillis();
                Files.writeString(diskPath, "mismatch-" + i);
                Files.setLastModifiedTime(diskPath, FileTime.fromMillis(originalMtime));
                checksumMismatched.add(path);
            }

            // 3. 删除 c4-c5 的磁盘文件，但保留 DB 记录
            List<String> staleFiles = new ArrayList<>();
            for (int i = 4; i <= 5; i++) {
                String path = consistentFiles.get(i - 1);
                Files.deleteIfExists(workspaceService.getDefaultWorkspaceRoot().resolve(path));
                staleFiles.add(path);
            }

            // 4. 直接往磁盘写 6 个 orphan 文件（不经过 API/DB）
            List<String> unindexedFiles = new ArrayList<>();
            for (int i = 1; i <= 6; i++) {
                String path = uniquePath + "/o" + i + ".sql";
                Path diskPath = workspaceService.getDefaultWorkspaceRoot().resolve(path);
                Files.writeString(diskPath, "orphan-" + i);
                unindexedFiles.add(path);
            }

            // 5. 第一次 reconcile（只扫 uniquePath 下，隔离其他测试数据）
            JsonNode firstReport = reconcile(uniquePath);
            System.out.println("First reconcile report: " + firstReport.toPrettyString());

            assertThat(toPathList(firstReport.path("checksumMismatches")))
                    .containsExactlyInAnyOrderElementsOf(checksumMismatched);
            assertThat(toPathList(firstReport.path("staleFiles")))
                    .containsExactlyInAnyOrderElementsOf(staleFiles);
            assertThat(toPathList(firstReport.path("unindexedFiles")))
                    .containsExactlyInAnyOrderElementsOf(unindexedFiles);

            // 6. 执行 repair（disk wins，只修复 uniquePath 下）
            JsonNode repairResult = repairDiskWins(uniquePath);
            System.out.println("Repair result: " + repairResult.toPrettyString());

            // 7. 再次 reconcile，所有 mismatch 列表应为空
            JsonNode secondReport = reconcile(uniquePath);
            System.out.println("Second reconcile report: " + secondReport.toPrettyString());

            assertThat(toPathList(secondReport.path("checksumMismatches"))).isEmpty();
            assertThat(toPathList(secondReport.path("staleFiles"))).isEmpty();
            assertThat(toPathList(secondReport.path("unindexedFiles"))).isEmpty();
            assertThat(toPathList(secondReport.path("mtimeMismatches"))).isEmpty();
        }

        @Test
        @DisplayName("content 检测到 CRC 不一致返回警告但仍有磁盘内容")
        void shouldReturnWarningWhenContentChecksumMismatch() throws Exception {
            createFolder(uniquePath);
            String path = uniquePath + "/mismatch-content.sql";
            Long fileId = createFile(path);
            acquireLock(fileId);
            saveFile(fileId, "original");

            // 直接改磁盘内容，mtime 由 write 自动更新
            Path diskPath = workspaceService.getDefaultWorkspaceRoot().resolve(path);
            String modified = "modified-outside";
            Files.writeString(diskPath, modified);

            // 读取 content：应返回磁盘真实内容，但 code 为 30714
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    "/api/files/content?fileId=" + fileId, HttpMethod.GET,
                    new HttpEntity<>(authHeaders(token)), JsonNode.class);
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(Objects.requireNonNull(response.getBody()).path("code").asInt()).isEqualTo(30714);
            assertThat(Objects.requireNonNull(response.getBody()).path("data").asText()).isEqualTo(modified);

            // 修复后再次读取，应恢复正常
            repairDiskWins();
            ResponseEntity<JsonNode> second = restTemplate.exchange(
                    "/api/files/content?fileId=" + fileId, HttpMethod.GET,
                    new HttpEntity<>(authHeaders(token)), JsonNode.class);
            assertThat(Objects.requireNonNull(second.getBody()).path("code").asInt()).isEqualTo(0);
            assertThat(Objects.requireNonNull(second.getBody()).path("data").asText()).isEqualTo(modified);
        }
    }

    // ==================== 阶段 2：软删除 + 自动 commit 测试 ====================

    @Nested
    @DisplayName("软删除流程：写入中删目录，验证 auto commit + delete commit 链")
    class Delete {

        /**
         * user1 正在保存，user2 删除目录
         */
        @Test
        @DisplayName("user1 写入中 user2 并发删目录，竞态下 commit 链和时间戳正确")
        void shouldAutoCommitBeforeDeleteAndVerifyCommitChain() throws Exception {
            // user1 和 user2
            createAnotherUser("user1");
            String user1Token = login("user1", "user1");
            String user2Token = loginAsAdmin();

            // 创建目录 A 和文件 A/file1.sql
            String folderPath = uniquePath;
            createFolder(folderPath);
            String filePath = folderPath + "/file1.sql";
            Long fileId = createFile(filePath);

            // user1 初次提交
            acquireLock(fileId, user1Token);
            saveFile(fileId, "initial content", user1Token);
            commit(List.of(fileId), "initial commit", user1Token);

            // user1 持有锁，准备写入
            acquireLock(fileId, user1Token);

            // 并发：user1 写入 + user2 删目录，在服务端 stripe 锁上真实竞争
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Long folderId = fileIdByPath(folderPath);

                Future<?> saveFuture = executor.submit(() ->
                        saveFile(fileId, "user1 latest content", user1Token));

                Future<?> deleteFuture = executor.submit(() ->
                        restTemplate.exchange(
                                "/api/files?fileId=" + folderId,
                                HttpMethod.DELETE,
                                new HttpEntity<>(authHeaders(user2Token)),
                                JsonNode.class));

                saveFuture.get(10, TimeUnit.SECONDS);
                deleteFuture.get(10, TimeUnit.SECONDS);
            } finally {
                executor.shutdown();
            }

            // === 验证 via JGit ===
            Path root = workspaceService.getDefaultWorkspaceRoot();
            try (Git git = Git.open(root.toFile());
                 RevWalk walk = new RevWalk(git.getRepository())) {

                ObjectId headId = git.getRepository().resolve(Constants.HEAD);
                RevCommit deleteCommit = walk.parseCommit(headId);

                // 验证最新 commit message 为 "delete A"
                assertThat(deleteCommit.getFullMessage())
                        .as("最新 commit 应为 delete commit").contains("delete " + folderPath);

                // 验证 DB deleted_at == HEAD commit time * 1000
                FileIndexEntity deleted = fileIndexService.getByPath(folderPath, FileIndexService.INCLUDE_DELETED);
                assertThat(deleted.getDeletedAt())
                        .as("DB deleted_at 应等于 delete commit 时间戳").isEqualTo(deleteCommit.getCommitTime() * 1000L);
                assertThat(deleted.getLatestCommitHash())
                        .as("DB latestCommitHash 应等于 delete commit hash").isEqualTo(deleteCommit.getName());

                // 验证父 commit message 为 "auto commit before delete"
                RevCommit autoCommit = walk.parseCommit(deleteCommit.getParent(0));
                assertThat(autoCommit.getFullMessage())
                        .as("delete 的父 commit 应为 auto commit before delete").contains("auto commit before delete");

                // 从父 commit 读取 A/file1.sql 内容，应等于 user1 最后写入内容
                try (TreeWalk treeWalk = TreeWalk.forPath(
                        git.getRepository(), filePath, autoCommit.getTree())) {
                    assertThat(treeWalk).as("auto commit 中应包含 file1.sql").isNotNull();
                    ObjectLoader loader = git.getRepository().open(treeWalk.getObjectId(0));
                    String content = new String(loader.getBytes(), StandardCharsets.UTF_8);
                    assertThat(content).as("auto commit 中 file1 内容应为 user1 最后写入内容")
                            .isEqualTo("user1 latest content");
                }
            }
        }

        /**
         * 磁盘文件容错删除的 5 个 case（ACBD = auto commit before delete）：
         * <pre>
         * case1：磁盘存在 → ACBD ✓ + FS删除 ✓ + delete commit ✓ + 索引更新 ✓
         * case2：磁盘存在，ACBD 后被外部删除（未 commit）→ ACBD ✓ + FS删除 ✗ + delete commit ✓ + 索引更新 ✓
         * case3：磁盘存在，ACBD 后被外部删除（已 commit）→ ACBD ✓ + FS删除 ✗ + delete commit ✗ + 索引更新 ✓
         * case4：磁盘不存在，曾被 commit 过，当前未提交改动 → ACBD ✗ + FS删除 ✗ + delete commit ✓ + 索引更新 ✓
         * case5：磁盘不存在，操作前已被删除且已 commit → ACBD ✗ + FS删除 ✗ + delete commit ✗ + 索引更新 ✓
         * </pre>
         * 当前只搭建了 case4（最典型的容错场景：文件已被外部删除但有 Git 历史）。
         */
        @Test
        @DisplayName("case4：磁盘文件已被外部删除（有 Git 历史但当前改动未提交），删除应补齐 git rm 并进入回收站")
        void shouldEnterTrashWhenFileDeletedExternallyWithUncommittedChanges() throws Exception {
            // 创建文件并提交，建立 Git 历史
            String filePath = uniquePath + "/case4.sql";
            Long fileId = createFile(filePath);
            acquireLock(fileId);
            saveFile(fileId, "committed content");
            CommitResultVO firstCommit = commit(List.of(fileId), "initial commit");

            // 写入新内容但不提交
            acquireLock(fileId);
            saveFile(fileId, "uncommitted content");

            // 模拟外部删除：直接从磁盘删除文件
            Path root = workspaceService.getDefaultWorkspaceRoot();
            Path diskPath = root.resolve(filePath);
            assertThat(Files.exists(diskPath)).as("磁盘文件应存在").isTrue();
            Files.delete(diskPath);
            assertThat(Files.exists(diskPath)).as("磁盘文件已被外部删除").isFalse();

            // 调用删除 API
            restTemplate.exchange(
                    "/api/files?fileId=" + fileId,
                    HttpMethod.DELETE,
                    new HttpEntity<>(authHeaders(token)),
                    JsonNode.class);

            // 验证：文件进入回收站，delete commit 生成了
            FileIndexEntity deleted = fileIndexService.getByPath(filePath, FileIndexService.INCLUDE_DELETED);
            assertThat(deleted).as("DB 索引应保留").isNotNull();
            assertThat(deleted.getDeletedAt()).as("deleted_at 应 > 0").isGreaterThan(0L);
            assertThat(deleted.getLatestCommitHash()).as("delete commit 应成功生成").isNotNull();

            // 验证 delete commit 的父 commit 为 firstCommit
            try (Git git = Git.open(root.toFile());
                 RevWalk walk = new RevWalk(git.getRepository())) {
                RevCommit deleteCommit = walk.parseCommit(ObjectId.fromString(deleted.getLatestCommitHash()));
                assertThat(deleteCommit.getParent(0).getName())
                        .as("delete commit 的父 commit 应为 initial commit").isEqualTo(firstCommit.getCommitHash());
            }
        }
    }

    // ==================== HTTP 辅助方法 ====================

    private void createFolder(String path) {
        CreateFolderDTO dto = new CreateFolderDTO();
        dto.setPath(path);
        restTemplate.exchange("/api/files/folder", HttpMethod.POST,
                new HttpEntity<>(dto, authHeaders(token)), JsonNode.class);
    }

    private Long createFile(String path) {
        CreateFileDTO dto = new CreateFileDTO();
        dto.setPath(path);
        ResponseEntity<JsonNode> response = restTemplate.exchange("/api/files/create", HttpMethod.POST,
                new HttpEntity<>(dto, authHeaders(token)), JsonNode.class);
        return Objects.requireNonNull(response.getBody()).path("data").path("fileId").asLong();
    }

    private void acquireLock(Long fileId) {
        LockDTO dto = new LockDTO();
        dto.setFileId(fileId);
        restTemplate.exchange("/api/files/lock/acquire", HttpMethod.POST,
                new HttpEntity<>(dto, authHeaders(token)), JsonNode.class);
    }

    private void releaseLock(Long fileId) {
        LockDTO dto = new LockDTO();
        dto.setFileId(fileId);
        restTemplate.exchange("/api/files/lock/release", HttpMethod.POST,
                new HttpEntity<>(dto, authHeaders(token)), JsonNode.class);
    }

    private void rename(Long fileId, String newName) {
        RenameDTO dto = new RenameDTO();
        dto.setFileId(fileId);
        dto.setNewName(newName);
        restTemplate.exchange("/api/files/rename", HttpMethod.POST,
                new HttpEntity<>(dto, authHeaders(token)), JsonNode.class);
    }

    private void saveFile(Long fileId, String content) {
        SaveFileDTO dto = new SaveFileDTO();
        dto.setFileId(fileId);
        dto.setContent(content);
        restTemplate.exchange("/api/files/save", HttpMethod.POST,
                new HttpEntity<>(dto, authHeaders(token)), JsonNode.class);
    }

    private CommitResultVO commit(List<Long> fileIds, String message) {
        CommitFileDTO dto = new CommitFileDTO();
        dto.setFileIds(fileIds);
        dto.setMessage(message);
        ResponseEntity<JsonNode> response = restTemplate.exchange("/api/files/commit", HttpMethod.POST,
                new HttpEntity<>(dto, authHeaders(token)), JsonNode.class);
        JsonNode data = Objects.requireNonNull(response.getBody()).path("data");
        CommitResultVO vo = JACKSON.parseObject(data.toString(), CommitResultVO.class);
        return vo;
    }

    private PublishVO publish(String displayName) {
        PublishDTO dto = new PublishDTO();
        dto.setDisplayName(displayName);
        ResponseEntity<JsonNode> response = restTemplate.exchange("/api/files/publish", HttpMethod.POST,
                new HttpEntity<>(dto, authHeaders(token)), JsonNode.class);
        JsonNode data = Objects.requireNonNull(response.getBody()).path("data");
        PublishVO vo = new PublishVO();
        vo.setTagName(data.path("tagName").asText());
        vo.setCommitHash(data.path("commitHash").asText());
        return vo;
    }

    private String lastCommitHash(Long fileId) {
        return lastCommitHash(fileId, token);
    }

    private String lastCommitHash(Long fileId, String userToken) {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                "/api/files/history?fileId=" + fileId + "&pageNum=1&pageSize=1",
                HttpMethod.GET, new HttpEntity<>(authHeaders(userToken)), JsonNode.class);
        return Objects.requireNonNull(response.getBody()).path("data").path("records").get(0).path("commitHash").asText();
    }

    private String content(Long fileId) {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                "/api/files/content?fileId=" + fileId,
                HttpMethod.GET, new HttpEntity<>(authHeaders(token)), JsonNode.class);
        return Objects.requireNonNull(response.getBody()).path("data").asText();
    }

    // 多用户场景辅助方法（带 token）

    private void acquireLock(Long fileId, String userToken) {
        LockDTO dto = new LockDTO();
        dto.setFileId(fileId);
        restTemplate.exchange("/api/files/lock/acquire", HttpMethod.POST,
                new HttpEntity<>(dto, authHeaders(userToken)), JsonNode.class);
    }

    private void saveFile(Long fileId, String content, String userToken) {
        SaveFileDTO dto = new SaveFileDTO();
        dto.setFileId(fileId);
        dto.setContent(content);
        restTemplate.exchange("/api/files/save", HttpMethod.POST,
                new HttpEntity<>(dto, authHeaders(userToken)), JsonNode.class);
    }

    private void commit(List<Long> fileIds, String message, String userToken) {
        CommitFileDTO dto = new CommitFileDTO();
        dto.setFileIds(fileIds);
        dto.setMessage(message);
        restTemplate.exchange("/api/files/commit", HttpMethod.POST,
                new HttpEntity<>(dto, authHeaders(userToken)), JsonNode.class);
    }

    private PublishVO publish(String displayName, String userToken) {
        PublishDTO dto = new PublishDTO();
        dto.setDisplayName(displayName);
        ResponseEntity<JsonNode> response = restTemplate.exchange("/api/files/publish", HttpMethod.POST,
                new HttpEntity<>(dto, authHeaders(userToken)), JsonNode.class);
        JsonNode data = Objects.requireNonNull(response.getBody()).path("data");
        PublishVO vo = new PublishVO();
        vo.setTagName(data.path("tagName").asText());
        vo.setCommitHash(data.path("commitHash").asText());
        return vo;
    }

    private String content(Long fileId, String userToken) {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                "/api/files/content?fileId=" + fileId,
                HttpMethod.GET, new HttpEntity<>(authHeaders(userToken)), JsonNode.class);
        return Objects.requireNonNull(response.getBody()).path("data").asText();
    }

    private JsonNode reconcile(String scope) {
        String url = "/api/admin/fs/reconcile";
        if (scope != null && !scope.isBlank()) {
            url += "?scope=" + scope;
        }
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.POST,
                new HttpEntity<>(authHeaders(token)), JsonNode.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(Objects.requireNonNull(response.getBody()).path("code").asInt()).isEqualTo(0);
        return Objects.requireNonNull(response.getBody()).path("data");
    }

    private JsonNode repairDiskWins() {
        return repairDiskWins(null);
    }

    private JsonNode repairDiskWins(String scope) {
        String url = "/api/admin/fs/repair?mode=disk_wins";
        if (scope != null && !scope.isBlank()) {
            url += "&scope=" + scope;
        }
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.POST,
                new HttpEntity<>(authHeaders(token)), JsonNode.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(Objects.requireNonNull(response.getBody()).path("code").asInt()).isEqualTo(0);
        return Objects.requireNonNull(response.getBody()).path("data");
    }

    private List<String> toPathList(JsonNode arrayNode) {
        List<String> result = new ArrayList<>();
        for (JsonNode node : arrayNode) {
            result.add(node.asText());
        }
        return result;
    }
}
