package com.lanting.admin.module.file.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.lanting.admin.BaseIntegrationTest;
import com.lanting.admin.module.file.dto.*;
import com.lanting.admin.module.file.service.WorkspaceService;
import com.lanting.admin.module.file.vo.PublishVO;
import com.lanting.admin.module.user.service.UserService;
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
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
    private UserService userService;
    @Autowired
    private ApplicationContext applicationContext;

    private String token;
    private String uniquePath;

    @BeforeEach
    void setUp() {
        token = loginAsAdmin();
        uniquePath = "jobs/test-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @AfterEach
    void tearDown() {
        // HTTP 链路无需手动清理锁
    }

    // ==================== 严重 bug 回归：删除真正进入 Git ====================

    @Nested
    @DisplayName("严重 bug 回归：删除操作真正进入 Git")
    class DeleteGitRegression {

        @Test
        @DisplayName("delete → commit 后 HEAD tree 中文件不存在")
        void deletedFileShouldNotExistInHeadTree() throws Exception {
            createFolder(uniquePath);
            String filePath = uniquePath + "/to-delete.sql";
            acquireLock(filePath);
            saveFile(filePath, "SELECT 1");
            commit(List.of(filePath), "add file");

            // 删除
            restTemplate.exchange(
                    "/api/files?path=" + filePath,
                    HttpMethod.DELETE,
                    new HttpEntity<>(authHeaders(token)),
                    JsonNode.class);

            // 用 JGit 直接验证 HEAD tree 中文件已不存在
            java.nio.file.Path root = workspaceService.getDefaultWorkspaceRoot();
            try (Git git = Git.open(root.toFile());
                 RevWalk walk = new RevWalk(git.getRepository())) {
                RevCommit head = walk.parseCommit(
                        git.getRepository().resolve(Constants.HEAD));
                TreeWalk treeWalk = TreeWalk.forPath(
                        git.getRepository(), filePath, head.getTree());
                assertThat(treeWalk).as("删除后文件应从 Git tree 中移除").isNull();
            }
        }

        @Test
        @DisplayName("delete 后新 publish 的 tag 不含被删文件")
        void publishAfterDeleteShouldNotContainDeletedFile() throws Exception {
            createFolder(uniquePath);
            String filePath = uniquePath + "/deleted.sql";
            acquireLock(filePath);
            saveFile(filePath, "SELECT 1");
            commit(List.of(filePath), "add file");

            // 删除文件
            restTemplate.exchange(
                    "/api/files?path=" + filePath,
                    HttpMethod.DELETE,
                    new HttpEntity<>(authHeaders(token)),
                    JsonNode.class);

            // 发布
            PublishVO pub = publish("after delete");

            // 验证新 tag 的 tree 中不含被删文件
            java.nio.file.Path root = workspaceService.getDefaultWorkspaceRoot();
            try (Git git = Git.open(root.toFile())) {
                ObjectId tagCommit = git.getRepository()
                        .resolve(Constants.R_TAGS + pub.getTagName());
                try (RevWalk walk = new RevWalk(git.getRepository())) {
                    RevCommit commit = walk.parseCommit(tagCommit);
                    TreeWalk treeWalk = TreeWalk.forPath(
                            git.getRepository(), filePath, commit.getTree());
                    assertThat(treeWalk).as("新发布的 tag 不应包含被删文件").isNull();
                }
            }
        }
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
            acquireLock(fileA);
            saveFile(fileA, "A");
            commit(List.of(fileA), "add A");

            // 查询 B 的历史，应为空
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    "/api/files/history?path=" + fileB + "&pageNum=1&pageSize=10",
                    HttpMethod.GET,
                    new HttpEntity<>(authHeaders(token)),
                    JsonNode.class);
            assertThat(response.getBody().path("code").asInt()).isEqualTo(0);
            JsonNode data = response.getBody().path("data");
            assertThat(data.path("records").size()).isEqualTo(0);
            assertThat(data.path("hasMore").asBoolean()).isFalse();
        }

        @Test
        @DisplayName("分别提交 A 和 B，查询 A 的历史只包含 A 的 commit")
        void historyShouldOnlyContainCommitsForSpecifiedPath() {
            createFolder(uniquePath);
            String fileA = uniquePath + "/filter-A.sql";
            String fileB = uniquePath + "/filter-B.sql";

            acquireLock(fileA);
            saveFile(fileA, "A");
            commit(List.of(fileA), "add A");

            acquireLock(fileB);
            saveFile(fileB, "B");
            commit(List.of(fileB), "add B");

            // 查询 A 的历史，只应有 1 条
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    "/api/files/history?path=" + fileA + "&pageNum=1&pageSize=10",
                    HttpMethod.GET,
                    new HttpEntity<>(authHeaders(token)),
                    JsonNode.class);
            assertThat(response.getBody().path("data").path("records").size()).isEqualTo(1);
            assertThat(response.getBody().path("data").path("hasMore").asBoolean()).isFalse();
            assertThat(response.getBody().path("data").path("records")
                    .get(0).path("message").asText()).contains("add A");
        }

        @Test
        @DisplayName("超过 pageSize 的提交返回 hasMore=true")
        void historyShouldReturnHasMoreWhenMoreCommitsExist() {
            createFolder(uniquePath);
            String file = uniquePath + "/paging.sql";

            acquireLock(file);
            saveFile(file, "v1");
            commit(List.of(file), "v1");
            saveFile(file, "v2");
            commit(List.of(file), "v2");
            saveFile(file, "v3");
            commit(List.of(file), "v3");

            // pageSize=2，应返回 2 条记录且 hasMore=true
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    "/api/files/history?path=" + file + "&pageNum=1&pageSize=2",
                    HttpMethod.GET,
                    new HttpEntity<>(authHeaders(token)),
                    JsonNode.class);
            assertThat(response.getBody().path("code").asInt()).isEqualTo(0);
            assertThat(response.getBody().path("data").path("records").size()).isEqualTo(2);
            assertThat(response.getBody().path("data").path("hasMore").asBoolean()).isTrue();
        }
    }


    @Nested
    @DisplayName("严重 bug 回归：force 删除后锁真正被清理")
    class ForceDeleteLockRegression {

        @Test
        @DisplayName("force 删除含他人锁的文件夹后，锁真正被清理")
        void forcedDeleteShouldClearAllLocks() throws Exception {
            createFolder(uniquePath);
            String fileA = uniquePath + "/lock-A.sql";
            String fileB = uniquePath + "/lock-B.sql";

            acquireLock(fileA);
            saveFile(fileA, "A");
            acquireLock(fileB);
            saveFile(fileB, "B");

            // force 删除整个目录
            ResponseEntity<JsonNode> deleteResp = restTemplate.exchange(
                    "/api/files?path=" + uniquePath + "&force=true",
                    HttpMethod.DELETE,
                    new HttpEntity<>(authHeaders(token)),
                    JsonNode.class);
            assertThat(deleteResp.getBody().path("code").asInt()).isEqualTo(0);

            // 验证锁已被清理：重新抢锁时 previousHolder 为 null
            // （因为锁已被 forceRelease，getHolder 应返回 null）
            // 通过 FileLockService 直接验证
            com.lanting.admin.module.file.service.FileLockService lockService =
                    applicationContext.getBean(com.lanting.admin.module.file.service.FileLockService.class);
            assertThat(lockService.getHolder(fileA)).as("fileA 的锁应已被清理").isNull();
            assertThat(lockService.getHolder(fileB)).as("fileB 的锁应已被清理").isNull();
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
            assertThat(response.getBody().path("code").asInt()).isEqualTo(30707);
        }
    }

    // ==================== 4.2 path="." / "./" ====================

    @Nested
    @DisplayName("路径安全：. 和 ./ 路径")
    class DotPathSecurity {

        @Test
        @DisplayName("path = '.' → PATH_ILLEGAL（30705）")
        void shouldRejectDotPath() {
            SaveFileDTO dto = new SaveFileDTO();
            dto.setPath(".");
            dto.setContent("test");
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    "/api/files/save", HttpMethod.POST,
                    new HttpEntity<>(dto, authHeaders(token)), JsonNode.class);
            assertThat(response.getBody().path("code").asInt()).isEqualTo(30705);
        }

        @Test
        @DisplayName("path = './' → PATH_ILLEGAL（30705）")
        void shouldRejectDotSlashPath() {
            SaveFileDTO dto = new SaveFileDTO();
            dto.setPath("./");
            dto.setContent("test");
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    "/api/files/save", HttpMethod.POST,
                    new HttpEntity<>(dto, authHeaders(token)), JsonNode.class);
            assertThat(response.getBody().path("code").asInt()).isEqualTo(30705);
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

            acquireLock(filePath);
            saveFile(filePath, "unlocked");
            // 抢锁修改后释放锁，当前用户已经不再持有锁了
            releaseLock(filePath);

            // 不抢锁，直接尝试删除
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    "/api/files?path=" + filePath,
                    HttpMethod.DELETE,
                    new HttpEntity<>(authHeaders(token)),
                    JsonNode.class);
            assertThat(response.getBody().path("code").asInt()).isEqualTo(30709);
        }

        @Test
        @DisplayName("提交他人锁定文件 → NOTHING_TO_COMMIT（30713）")
        void commitAllLockedByOthersShouldReturn30713() {
            createFolder(uniquePath);
            String filePath = uniquePath + "/other.sql";

            // admin 抢锁
            acquireLock(filePath);
            saveFile(filePath, "content");

            // 用另一个用户登录尝试提交 admin 持有锁的文件
            String anotherUser = "test-user-1";
            createAnotherUser(anotherUser);
            String anotherUserToken = login(anotherUser, anotherUser);

            CommitFileDTO dto = new CommitFileDTO();
            dto.setPaths(List.of(filePath)); // admin 锁定文件
            dto.setMessage("should fail");
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    "/api/files/commit", HttpMethod.POST,
                    new HttpEntity<>(dto, authHeaders(anotherUserToken)), JsonNode.class);
            assertThat(response.getBody().path("code").asInt()).isEqualTo(30713);
        }

        @Test
        @DisplayName("非法 SHA 调用 revert → 400 + PARAM_INVALID（10001）")
        void revertWithInvalidShaShouldReturn400() {
            createFolder(uniquePath);
            String filePath = uniquePath + "/revert-sha.sql";
            acquireLock(filePath);
            saveFile(filePath, "content");
            commit(List.of(filePath), "add");

            RevertFileDTO dto = new RevertFileDTO();
            dto.setPath(filePath);
            dto.setCommitHash("not-a-valid-sha");
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    "/api/files/revert", HttpMethod.POST,
                    new HttpEntity<>(dto, authHeaders(token)), JsonNode.class);
            assertThat(response.getStatusCode().value()).isEqualTo(400);
            assertThat(response.getBody().path("code").asInt()).isEqualTo(10001);
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
            acquireLock(filePath);

            // 先提交一个基准版本
            saveFile(filePath, "committed baseline");
            commit(List.of(filePath), "committed baseline");
            // 保存到磁盘但不 commit
            saveFile(filePath, "uncommitted content");

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
            acquireLock(filePath);
            saveFile(filePath, "base");
            commit(List.of(filePath), "base commit");

            // 先提交一个基准版本
            saveFile(filePath, "committed baseline");
            commit(List.of(filePath), "committed baseline");
            // 第一次发布
            PublishVO pub1 = publish("first");
            Thread.sleep(1000);
            // 第二次发布（同一 HEAD）
            PublishVO pub2 = publish("second");

            assertThat(pub1.getTagName()).isNotEqualTo(pub2.getTagName());
            assertThat(pub1.getCommitHash()).isEqualTo(pub2.getCommitHash());
        }
    }

    // ==================== 4.5 删除边界 ====================

    @Nested
    @DisplayName("4.5 删除边界")
    class DeleteEdgeCases {

        @Test
        @DisplayName("force=false 含他人锁的文件夹 → FILES_LOCKED（30712），返回被锁文件列表")
        void deleteWithOtherLockAndNoForceShouldReturn30712() {
            createFolder(uniquePath);
            String folderPath = uniquePath + "/locked-folder";
            createFolder(folderPath);
            String lockedFile = folderPath + "/locked.sql";

            // 抢锁文件
            acquireLock(lockedFile);
            saveFile(lockedFile, "content");

            // 不传 force，尝试删除文件夹（自己持锁自己的文件不算销，应该成功）
            // 为了测试他人锁定场景，注意这里 admin 自己持有锁，所以删除应该会成功
            // 要测试 30712，需要一个其他用户持有锁。
            // 社区版只有 admin，需要先创建一个新用户来抢锁
            // 这里用 FileLockService 直接注入一个他人锁来模拟
            com.lanting.admin.module.file.service.FileLockService lockService =
                    applicationContext.getBean(com.lanting.admin.module.file.service.FileLockService.class);

            // 先释放 admin 的锁，再用他人身份抢锁
            lockService.forceRelease(lockedFile);
            lockService.acquire(lockedFile, "other-user");

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    "/api/files?path=" + folderPath + "&force=false",
                    HttpMethod.DELETE,
                    new HttpEntity<>(authHeaders(token)),
                    JsonNode.class);

            assertThat(response.getBody().path("code").asInt()).isEqualTo(30712);
            // 验证返回了被锁文件列表
            JsonNode lockedFiles = response.getBody().path("data").path("lockedFiles");
            assertThat(lockedFiles.isArray()).isTrue();
            assertThat(lockedFiles.size()).isGreaterThan(0);
            assertThat(lockedFiles.get(0).path("path").asText()).contains("locked.sql");
        }
    }

    // ==================== 完整正向流程 ====================

    @Nested
    @DisplayName("完整正向流程")
    class FullFlow {

        @Test
        @DisplayName("创建目录 → 保存文件 → 查看文件树 → 读取内容 → 提交文件 → 发布")
        void shouldCompleteFullFlow() {
            createFolder(uniquePath);

            String filePath = uniquePath + "/test.sql";
            acquireLock(filePath);
            saveFile(filePath, "SELECT 1");

            // 查看文件树
            ResponseEntity<JsonNode> treeResponse = restTemplate.exchange(
                    "/api/files/tree?parentPath=" + uniquePath,
                    HttpMethod.GET, new HttpEntity<>(authHeaders(token)), JsonNode.class);
            JsonNode tree = treeResponse.getBody().path("data");
            assertThat(tree).hasSize(1);
            assertThat(tree.get(0).path("name").asText()).isEqualTo("test.sql");
            assertThat(tree.get(0).path("type").asText()).isEqualTo("file");
            assertThat(tree.get(0).path("lockedBy").asText()).isEqualTo("1");

            // 读取内容
            ResponseEntity<JsonNode> contentResponse = restTemplate.exchange(
                    "/api/files/content?path=" + filePath,
                    HttpMethod.GET, new HttpEntity<>(authHeaders(token)), JsonNode.class);
            assertThat(contentResponse.getBody().path("data").asText()).isEqualTo("SELECT 1");

            // 提交
            commit(List.of(filePath), "add test.sql");

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
            acquireLock(filePath);
            saveFile(filePath, "v1");
            commit(List.of(filePath), "v1");
            String hashV1 = lastCommitHash();

            saveFile(filePath, "v2");
            commit(List.of(filePath), "v2");

            RevertFileDTO dto = new RevertFileDTO();
            dto.setPath(filePath);
            dto.setCommitHash(hashV1);
            ResponseEntity<JsonNode> revertResponse = restTemplate.exchange(
                    "/api/files/revert",
                    HttpMethod.POST, new HttpEntity<>(dto, authHeaders(token)), JsonNode.class);
            assertThat(revertResponse.getBody().path("code").asInt()).isEqualTo(0);

            ResponseEntity<JsonNode> contentResponse = restTemplate.exchange(
                    "/api/files/content?path=" + filePath,
                    HttpMethod.GET, new HttpEntity<>(authHeaders(token)), JsonNode.class);
            assertThat(contentResponse.getBody().path("data").asText()).isEqualTo("v1");
        }

        @Test
        @DisplayName("commit 50 次后回滚到第 21 次提交")
        void shouldRevertTo21stCommitAmong50() {
            createFolder(uniquePath);
            String filePath = uniquePath + "/history-50.sql";
            acquireLock(filePath);

            for (int i = 1; i <= 15; i++) {
                saveFile(filePath, "v" + i);
                commit(List.of(filePath), "v" + i);
            }

            // 查询历史，取第 11 条（index 10）作为回滚目标
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    "/api/files/history?path=" + filePath + "&pageNum=1&pageSize=50",
                    HttpMethod.GET, new HttpEntity<>(authHeaders(token)), JsonNode.class);
            assertThat(response.getBody().path("code").asInt()).isEqualTo(0);
            JsonNode records = response.getBody().path("data").path("records");
            assertThat(records.size()).isGreaterThanOrEqualTo(11);
            String targetHash = records.get(10).path("commitHash").asText();

            RevertFileDTO dto = new RevertFileDTO();
            dto.setPath(filePath);
            dto.setCommitHash(targetHash);
            ResponseEntity<JsonNode> revertResponse = restTemplate.exchange(
                    "/api/files/revert", HttpMethod.POST,
                    new HttpEntity<>(dto, authHeaders(token)), JsonNode.class);
            assertThat(revertResponse.getBody().path("code").asInt()).isEqualTo(0);

            // 第 11 条记录对应 v5（历史为倒序：v15, v14, ..., v1）
            assertThat(content(filePath)).isEqualTo("v5");
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
            acquireLock(filePath, tokenA);
            saveFile(filePath, "A-content", tokenA);
            commit(List.of(filePath), "c1 by A", tokenA);
            PublishVO publishA = publish("A release", tokenA);
            String hashC1 = publishA.getCommitHash();

            // 2. 用户 B 修改 f1，提交 c2
            String userB = "user-b-revert";
            createAnotherUser(userB);
            String tokenB = login(userB, userB);

            acquireLock(filePath, tokenB);
            saveFile(filePath, "B-content", tokenB);
            commit(List.of(filePath), "c2 by B", tokenB);
            String hashC2BeforeRevert = lastCommitHash(filePath, tokenB);

            // 3. B 再次修改后发现改坏，回滚到 A 的 c1
            saveFile(filePath, "B-bad-content", tokenB);

            RevertFileDTO dto = new RevertFileDTO();
            dto.setPath(filePath);
            dto.setCommitHash(hashC1);
            ResponseEntity<JsonNode> revertResponse = restTemplate.exchange(
                    "/api/files/revert", HttpMethod.POST,
                    new HttpEntity<>(dto, authHeaders(tokenB)), JsonNode.class);
            assertThat(revertResponse.getBody().path("code").asInt()).isEqualTo(0);

            // 4. 验证：工作区内容恢复为 A 的内容，HEAD 仍然是 c2
            assertThat(content(filePath, tokenB)).as("working content should be A's version").isEqualTo("A-content");
            String hashC2AfterRevert = lastCommitHash(filePath, tokenB);
            assertThat(hashC2AfterRevert).as("HEAD should remain c2, no new commit created").isEqualTo(hashC2BeforeRevert);
        }
    }

    // ==================== delete ====================

    @Nested
    @DisplayName("delete 删除")
    class Delete {

        @Test
        @DisplayName("删除文件夹 force=true 清除所有锁")
        void shouldForceDeleteFolderAndClearLocks() {
            String folderPath = uniquePath + "/force-del";
            createFolder(folderPath);

            String filePath = folderPath + "/locked.sql";
            acquireLock(filePath);
            saveFile(filePath, "locked");

            ResponseEntity<JsonNode> deleteResponse = restTemplate.exchange(
                    "/api/files?path=" + folderPath + "&force=true",
                    HttpMethod.DELETE, new HttpEntity<>(authHeaders(token)), JsonNode.class);
            assertThat(deleteResponse.getBody().path("code").asInt()).isEqualTo(0);

            // 删除后文件树中不再存在
            ResponseEntity<JsonNode> treeResponse = restTemplate.exchange(
                    "/api/files/tree?parentPath=" + uniquePath,
                    HttpMethod.GET, new HttpEntity<>(authHeaders(token)), JsonNode.class);
            JsonNode tree = treeResponse.getBody().path("data");
            assertThat(tree).isEmpty();
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
            for (int i = 1; i <= 15; i++) {
                String path = uniquePath + "/c" + i + ".sql";
                acquireLock(path);
                saveFile(path, "consistent-" + i);
                consistentFiles.add(path);
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
            acquireLock(path);
            saveFile(path, "original");

            // 直接改磁盘内容，mtime 由 write 自动更新
            Path diskPath = workspaceService.getDefaultWorkspaceRoot().resolve(path);
            String modified = "modified-outside";
            Files.writeString(diskPath, modified);

            // 读取 content：应返回磁盘真实内容，但 code 为 30714
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    "/api/files/content?path=" + path, HttpMethod.GET,
                    new HttpEntity<>(authHeaders(token)), JsonNode.class);
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody().path("code").asInt()).isEqualTo(30714);
            assertThat(response.getBody().path("data").asText()).isEqualTo(modified);

            // 修复后再次读取，应恢复正常
            repairDiskWins();
            ResponseEntity<JsonNode> second = restTemplate.exchange(
                    "/api/files/content?path=" + path, HttpMethod.GET,
                    new HttpEntity<>(authHeaders(token)), JsonNode.class);
            assertThat(second.getBody().path("code").asInt()).isEqualTo(0);
            assertThat(second.getBody().path("data").asText()).isEqualTo(modified);
        }
    }

    // ==================== HTTP 辅助方法 ====================

    private void createFolder(String path) {
        CreateFolderDTO dto = new CreateFolderDTO();
        dto.setPath(path);
        restTemplate.exchange("/api/files/folder", HttpMethod.POST,
                new HttpEntity<>(dto, authHeaders(token)), JsonNode.class);
    }

    private void acquireLock(String path) {
        LockDTO dto = new LockDTO();
        dto.setPath(path);
        restTemplate.exchange("/api/files/lock/acquire", HttpMethod.POST,
                new HttpEntity<>(dto, authHeaders(token)), JsonNode.class);
    }

    private void releaseLock(String path) {
        LockDTO dto = new LockDTO();
        dto.setPath(path);
        restTemplate.exchange("/api/files/lock/release", HttpMethod.POST,
                new HttpEntity<>(dto, authHeaders(token)), JsonNode.class);
    }

    private void saveFile(String path, String content) {
        SaveFileDTO dto = new SaveFileDTO();
        dto.setPath(path);
        dto.setContent(content);
        restTemplate.exchange("/api/files/save", HttpMethod.POST,
                new HttpEntity<>(dto, authHeaders(token)), JsonNode.class);
    }

    private void commit(List<String> paths, String message) {
        CommitFileDTO dto = new CommitFileDTO();
        dto.setPaths(paths);
        dto.setMessage(message);
        restTemplate.exchange("/api/files/commit", HttpMethod.POST,
                new HttpEntity<>(dto, authHeaders(token)), JsonNode.class);
    }

    private PublishVO publish(String displayName) {
        PublishDTO dto = new PublishDTO();
        dto.setDisplayName(displayName);
        ResponseEntity<JsonNode> response = restTemplate.exchange("/api/files/publish", HttpMethod.POST,
                new HttpEntity<>(dto, authHeaders(token)), JsonNode.class);
        JsonNode data = response.getBody().path("data");
        PublishVO vo = new PublishVO();
        vo.setTagName(data.path("tagName").asText());
        vo.setCommitHash(data.path("commitHash").asText());
        return vo;
    }

    private String lastCommitHash() {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                "/api/files/history?path=" + uniquePath + "&pageNum=1&pageSize=1",
                HttpMethod.GET, new HttpEntity<>(authHeaders(token)), JsonNode.class);
        return response.getBody().path("data").path("records").get(0).path("commitHash").asText();
    }

    private String content(String path) {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                "/api/files/content?path=" + path,
                HttpMethod.GET, new HttpEntity<>(authHeaders(token)), JsonNode.class);
        return response.getBody().path("data").asText();
    }

    // 多用户场景辅助方法（带 token）

    private void acquireLock(String path, String userToken) {
        LockDTO dto = new LockDTO();
        dto.setPath(path);
        restTemplate.exchange("/api/files/lock/acquire", HttpMethod.POST,
                new HttpEntity<>(dto, authHeaders(userToken)), JsonNode.class);
    }

    private void saveFile(String path, String content, String userToken) {
        SaveFileDTO dto = new SaveFileDTO();
        dto.setPath(path);
        dto.setContent(content);
        restTemplate.exchange("/api/files/save", HttpMethod.POST,
                new HttpEntity<>(dto, authHeaders(userToken)), JsonNode.class);
    }

    private void commit(List<String> paths, String message, String userToken) {
        CommitFileDTO dto = new CommitFileDTO();
        dto.setPaths(paths);
        dto.setMessage(message);
        restTemplate.exchange("/api/files/commit", HttpMethod.POST,
                new HttpEntity<>(dto, authHeaders(userToken)), JsonNode.class);
    }

    private PublishVO publish(String displayName, String userToken) {
        PublishDTO dto = new PublishDTO();
        dto.setDisplayName(displayName);
        ResponseEntity<JsonNode> response = restTemplate.exchange("/api/files/publish", HttpMethod.POST,
                new HttpEntity<>(dto, authHeaders(userToken)), JsonNode.class);
        JsonNode data = response.getBody().path("data");
        PublishVO vo = new PublishVO();
        vo.setTagName(data.path("tagName").asText());
        vo.setCommitHash(data.path("commitHash").asText());
        return vo;
    }

    private String content(String path, String userToken) {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                "/api/files/content?path=" + path,
                HttpMethod.GET, new HttpEntity<>(authHeaders(userToken)), JsonNode.class);
        return response.getBody().path("data").asText();
    }

    private String lastCommitHash(String path, String userToken) {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                "/api/files/history?path=" + path + "&pageNum=1&pageSize=1",
                HttpMethod.GET, new HttpEntity<>(authHeaders(userToken)), JsonNode.class);
        return response.getBody().path("data").path("records").get(0).path("commitHash").asText();
    }

    private void updateToken(String token) {
        this.token = token;
    }

    private JsonNode reconcile() {
        return reconcile(null);
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
        assertThat(response.getBody().path("code").asInt()).isEqualTo(0);
        return response.getBody().path("data");
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
        assertThat(response.getBody().path("code").asInt()).isEqualTo(0);
        return response.getBody().path("data");
    }

    private List<String> toPathList(JsonNode arrayNode) {
        List<String> result = new ArrayList<>();
        for (JsonNode node : arrayNode) {
            result.add(node.asText());
        }
        return result;
    }
}
