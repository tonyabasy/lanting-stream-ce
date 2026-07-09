package com.lanting.admin.module.file.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.lanting.admin.BaseIntegrationTest;
import com.lanting.admin.module.file.dto.*;
import org.junit.jupiter.api.*;
import org.springframework.http.*;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FileController HTTP 接口集成测试。覆盖 review 接口、鉴权和错误码。
 *
 * @author wangzhao
 */
@DisplayName("FileController HTTP 集成测试")
class FileControllerTest extends BaseIntegrationTest {

    private String token;
    private String uniquePath;

    @BeforeEach
    void setUp() {
        token = loginAsAdmin();
        uniquePath = "jobs/controller-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @AfterEach
    void tearDown() {
        // HTTP 测试无需手动清理锁
    }

    private String lastCommitHash(String path) {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                "/api/files/history?path=" + path + "&pageNum=1&pageSize=1",
                HttpMethod.GET, new HttpEntity<>(authHeaders(token)), JsonNode.class);
        return Objects.requireNonNull(response.getBody()).path("data").path("records").get(0).path("commitHash").asText();
    }

    // ==================== review 添加与查询 ====================

    @Nested
    @DisplayName("review 接口")
    class ReviewApi {

        @Test
        @DisplayName("添加 review 并查询")
        void shouldAddAndListReview() {
            // 创建文件夹
            // placeholder, using map
            var folderBody = new HashMap<String, String>();
            folderBody.put("path", uniquePath);
            restTemplate.exchange(
                    "/api/files/folder",
                    HttpMethod.POST,
                    new HttpEntity<>(folderBody, authHeaders(token)),
                    JsonNode.class);

            // 抢锁
            var lockBody = new HashMap<String, String>();
            lockBody.put("path", uniquePath + "/review.sql");
            restTemplate.exchange(
                    "/api/files/lock/acquire",
                    HttpMethod.POST,
                    new HttpEntity<>(lockBody, authHeaders(token)),
                    JsonNode.class);

            // 保存文件
            var saveBody = new HashMap<String, Object>();
            saveBody.put("path", uniquePath + "/review.sql");
            saveBody.put("content", "SELECT 1");
            restTemplate.exchange(
                    "/api/files/save",
                    HttpMethod.POST,
                    new HttpEntity<>(saveBody, authHeaders(token)),
                    JsonNode.class);

            // 提交
            var commitBody = new HashMap<String, Object>();
            commitBody.put("paths", List.of(uniquePath + "/review.sql"));
            commitBody.put("message", "for review test");
            restTemplate.exchange(
                    "/api/files/commit",
                    HttpMethod.POST,
                    new HttpEntity<>(commitBody, authHeaders(token)),
                    JsonNode.class);

            // 发布
            var publishBody = new HashMap<String, String>();
            publishBody.put("displayName", "review release");
            var publishResponse = restTemplate.exchange(
                    "/api/files/publish",
                    HttpMethod.POST,
                    new HttpEntity<>(publishBody, authHeaders(token)),
                    JsonNode.class);
            String tagName = Objects.requireNonNull(publishResponse.getBody()).path("data").path("tagName").asText();
            assertThat(tagName).isNotBlank();

            // 添加 review
            ReviewDTO review = new ReviewDTO();
            review.setTagName(tagName);
            review.setComment("looks good");
            ResponseEntity<JsonNode> addResponse = restTemplate.exchange(
                    "/api/files/review",
                    HttpMethod.POST,
                    new HttpEntity<>(review, authHeaders(token)),
                    JsonNode.class);

            assertThat(addResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(Objects.requireNonNull(addResponse.getBody()).path("code").asInt()).isEqualTo(0);

            // 查询 review 列表
            ResponseEntity<JsonNode> listResponse = restTemplate.exchange(
                    "/api/files/review?tagName=" + tagName,
                    HttpMethod.GET,
                    new HttpEntity<>(authHeaders(token)),
                    JsonNode.class);

            assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode data = Objects.requireNonNull(listResponse.getBody()).path("data");
            assertThat(data.isArray()).isTrue();
            assertThat(data.size()).isGreaterThanOrEqualTo(1);
        }
    }

    // ==================== 鉴权与错误码 ====================

    @Nested
    @DisplayName("鉴权与校验")
    class AuthAndValidation {

        @Test
        @DisplayName("未登录访问 → 401")
        void shouldReturn401WhenUnauthorized() {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    "/api/files/tree?parentPath=",
                    HttpMethod.GET,
                    new HttpEntity<>(jsonHeaders()),
                    JsonNode.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("参数校验缺失 → 400")
        void shouldReturn400WhenParamMissing() {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    "/api/files/folder",
                    HttpMethod.POST,
                    new HttpEntity<>("{}", authHeaders(token)),
                    JsonNode.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("save 未持锁 → 30709 FILE_LOCKED")
        void shouldReturnFileLockedWhenNotHolder() {
            String file = uniquePath + "/locked.sql";
            SaveFileDTO dto = new SaveFileDTO();
            dto.setPath(file);
            dto.setContent("test");

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    "/api/files/save",
                    HttpMethod.POST,
                    new HttpEntity<>(dto, authHeaders(token)),
                    JsonNode.class);

            assertThat(Objects.requireNonNull(response.getBody()).path("code").asInt()).isEqualTo(30709);
        }

        @Test
        @DisplayName("删除不存在的文件 → 30702 FILE_NOT_FOUND")
        void shouldReturnFileNotFound() {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    "/api/files?path=nonexistent/path.sql",
                    HttpMethod.DELETE,
                    new HttpEntity<>(authHeaders(token)),
                    JsonNode.class);

            assertThat(Objects.requireNonNull(response.getBody()).path("code").asInt()).isEqualTo(30702);
        }
    }

    // ==================== 路径安全 ====================

    @Nested
    @DisplayName("路径安全校验")
    class PathSecurity {

        @Test
        @DisplayName("path 含 '..' → PATH_ILLEGAL（30705）")
        void shouldRejectPathWithDotDot() {
            SaveFileDTO dto = new SaveFileDTO();
            dto.setPath("jobs/../../../etc/passwd");
            dto.setContent("test");
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    "/api/files/save", HttpMethod.POST,
                    new HttpEntity<>(dto, authHeaders(token)), JsonNode.class);
            assertThat(Objects.requireNonNull(response.getBody()).path("code").asInt()).isEqualTo(30705);
        }

        @Test
        @DisplayName("绝对路径 → PATH_ILLEGAL（30705）")
        void shouldRejectAbsolutePath() {
            SaveFileDTO dto = new SaveFileDTO();
            dto.setPath("/etc/passwd");
            dto.setContent("test");
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    "/api/files/save", HttpMethod.POST,
                    new HttpEntity<>(dto, authHeaders(token)), JsonNode.class);
            assertThat(Objects.requireNonNull(response.getBody()).path("code").asInt()).isEqualTo(30705);
        }

        @Test
        @DisplayName("反斜杠路径 → PATH_ILLEGAL（30705）")
        void shouldRejectBackslashPath() {
            SaveFileDTO dto = new SaveFileDTO();
            dto.setPath("jobs\\a.sql");
            dto.setContent("test");
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    "/api/files/save", HttpMethod.POST,
                    new HttpEntity<>(dto, authHeaders(token)), JsonNode.class);
            assertThat(Objects.requireNonNull(response.getBody()).path("code").asInt()).isEqualTo(30705);
        }

        @Test
        @DisplayName(".lanting 目录 → LANTING_DIR_FORBIDDEN（30706）")
        void shouldRejectLantingDir() {
            SaveFileDTO dto = new SaveFileDTO();
            dto.setPath(".lanting/config.json");
            dto.setContent("{}");
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    "/api/files/save", HttpMethod.POST,
                    new HttpEntity<>(dto, authHeaders(token)), JsonNode.class);
            assertThat(Objects.requireNonNull(response.getBody()).path("code").asInt()).isEqualTo(30706);
        }

        @Test
        @DisplayName(".git 目录 → LANTING_DIR_FORBIDDEN（30706）")
        void shouldRejectGitDir() {
            SaveFileDTO dto = new SaveFileDTO();
            dto.setPath(".git/config");
            dto.setContent("test");
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    "/api/files/save", HttpMethod.POST,
                    new HttpEntity<>(dto, authHeaders(token)), JsonNode.class);
            assertThat(Objects.requireNonNull(response.getBody()).path("code").asInt()).isEqualTo(30706);
        }
    }

    // ==================== 文件类型和大小 ====================

    @Nested
    @DisplayName("文件类型和大小校验")
    class FileTypeAndSize {

        private String lockedPath;

        @BeforeEach
        void acquireTestLock() {
            // 提前抢锁，使后续 save 请求能进入类型校验
            lockedPath = uniquePath + "/test.txt";
            var lockBody = new HashMap<String, String>();
            lockBody.put("path", lockedPath);
            restTemplate.exchange("/api/files/lock/acquire", HttpMethod.POST,
                    new HttpEntity<>(lockBody, authHeaders(token)), JsonNode.class);
        }

        @Test
        @DisplayName(".txt 文件 → FILE_TYPE_NOT_ALLOWED（30703）")
        void shouldRejectTxtFile() {
            SaveFileDTO dto = new SaveFileDTO();
            dto.setPath(lockedPath);
            dto.setContent("test");
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    "/api/files/save", HttpMethod.POST,
                    new HttpEntity<>(dto, authHeaders(token)), JsonNode.class);
            assertThat(Objects.requireNonNull(response.getBody()).path("code").asInt()).isEqualTo(30703);
        }

        @Test
        @DisplayName("无扩展名文件 → FILE_TYPE_NOT_ALLOWED（30703）")
        void shouldRejectFileWithNoExtension() {
            String noExtPath = uniquePath + "/noext";
            var lockBody = new HashMap<String, String>();
            lockBody.put("path", noExtPath);
            restTemplate.exchange("/api/files/lock/acquire", HttpMethod.POST,
                    new HttpEntity<>(lockBody, authHeaders(token)), JsonNode.class);

            SaveFileDTO dto = new SaveFileDTO();
            dto.setPath(noExtPath);
            dto.setContent("test");
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    "/api/files/save", HttpMethod.POST,
                    new HttpEntity<>(dto, authHeaders(token)), JsonNode.class);
            assertThat(Objects.requireNonNull(response.getBody()).path("code").asInt()).isEqualTo(30703);
        }

        @Test
        @DisplayName(".sql 文件内容等于 1MB 应通过")
        void shouldAcceptFileExactly1MB() {
            String sqlPath = uniquePath + "/exactly1mb.sql";
            var lockBody = new HashMap<String, String>();
            lockBody.put("path", sqlPath);
            restTemplate.exchange("/api/files/lock/acquire", HttpMethod.POST,
                    new HttpEntity<>(lockBody, authHeaders(token)), JsonNode.class);

            // 生成正好 1MB 的内容（ASCII 字符，每字符 1 字节）
            String content = "A".repeat(1024 * 1024);
            SaveFileDTO dto = new SaveFileDTO();
            dto.setPath(sqlPath);
            dto.setContent(content);
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    "/api/files/save", HttpMethod.POST,
                    new HttpEntity<>(dto, authHeaders(token)), JsonNode.class);
            assertThat(Objects.requireNonNull(response.getBody()).path("code").asInt()).isEqualTo(0);
        }

        @Test
        @DisplayName("文件内容超过 1MB → FILE_SIZE_EXCEEDED（30704）")
        void shouldRejectFileOver1MB() {
            String sqlPath = uniquePath + "/over1mb.sql";
            var lockBody = new HashMap<String, String>();
            lockBody.put("path", sqlPath);
            restTemplate.exchange("/api/files/lock/acquire", HttpMethod.POST,
                    new HttpEntity<>(lockBody, authHeaders(token)), JsonNode.class);

            // 1MB + 1 字节
            String content = "A".repeat(1024 * 1024 + 1);
            SaveFileDTO dto = new SaveFileDTO();
            dto.setPath(sqlPath);
            dto.setContent(content);
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    "/api/files/save", HttpMethod.POST,
                    new HttpEntity<>(dto, authHeaders(token)), JsonNode.class);
            assertThat(Objects.requireNonNull(response.getBody()).path("code").asInt()).isEqualTo(30704);
        }

        @Test
        @DisplayName("空内容 null / '' — 写入0 字节，不报错")
        void shouldAcceptEmptyContent() {
            String sqlPath = uniquePath + "/empty.sql";
            var lockBody = new HashMap<String, String>();
            lockBody.put("path", sqlPath);
            restTemplate.exchange("/api/files/lock/acquire", HttpMethod.POST,
                    new HttpEntity<>(lockBody, authHeaders(token)), JsonNode.class);

            SaveFileDTO dto = new SaveFileDTO();
            dto.setPath(sqlPath);
            dto.setContent("");
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    "/api/files/save", HttpMethod.POST,
                    new HttpEntity<>(dto, authHeaders(token)), JsonNode.class);
            assertThat(Objects.requireNonNull(response.getBody()).path("code").asInt()).isEqualTo(0);
        }
    }

    // ==================== diff 边界 ====================

    @Nested
    @DisplayName("diff 边界测试")
    class DiffBoundary {

        @Test
        @DisplayName("非法 commit SHA → 400 + PARAM_INVALID（10001）")
        void shouldReturn400ForInvalidSha() {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    "/api/files/diff?path=jobs/a.sql&from=invalid-sha&to=deadbeef1234567890",
                    HttpMethod.GET,
                    new HttpEntity<>(authHeaders(token)),
                    JsonNode.class);
            assertThat(response.getStatusCode().value()).isEqualTo(400);
            assertThat(Objects.requireNonNull(response.getBody()).path("code").asInt()).isEqualTo(10001);
        }

        @Test
        @DisplayName("from == to → 返回空字符串，不报错")
        void shouldReturnEmptyDiffWhenFromEqualsTo() {
            // 需要先有一个合法 commit
            String folderPath = uniquePath + "/diff-test";
            CreateFolderDTO folder = new CreateFolderDTO();
            folder.setPath(folderPath);
            restTemplate.exchange("/api/files/folder", HttpMethod.POST,
                    new HttpEntity<>(folder, authHeaders(token)), JsonNode.class);

            String filePath = folderPath + "/a.sql";
            var lockBody = new HashMap<String, String>();
            lockBody.put("path", filePath);
            restTemplate.exchange("/api/files/lock/acquire", HttpMethod.POST,
                    new HttpEntity<>(lockBody, authHeaders(token)), JsonNode.class);

            SaveFileDTO save = new SaveFileDTO();
            save.setPath(filePath);
            save.setContent("SELECT 1");
            restTemplate.exchange("/api/files/save", HttpMethod.POST,
                    new HttpEntity<>(save, authHeaders(token)), JsonNode.class);

            CommitFileDTO commitDTO = new CommitFileDTO();
            commitDTO.setPaths(List.of(filePath));
            commitDTO.setMessage("add");
            restTemplate.exchange("/api/files/commit", HttpMethod.POST,
                    new HttpEntity<>(commitDTO, authHeaders(token)), JsonNode.class);

            String hash = lastCommitHash(folderPath);

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    "/api/files/diff?path=" + filePath + "&from=" + hash + "&to=" + hash,
                    HttpMethod.GET,
                    new HttpEntity<>(authHeaders(token)),
                    JsonNode.class);
            assertThat(Objects.requireNonNull(response.getBody()).path("code").asInt()).isEqualTo(0);
            assertThat(Objects.requireNonNull(response.getBody()).path("data").asText()).isEmpty();
        }
    }
}
