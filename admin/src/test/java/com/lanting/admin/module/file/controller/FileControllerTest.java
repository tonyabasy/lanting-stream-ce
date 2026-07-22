package com.lanting.admin.module.file.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.lanting.admin.BaseIntegrationTest;
import com.lanting.admin.module.file.dto.*;
import com.lanting.admin.module.file.service.FileIndexService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;

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

    @Autowired
    private FileIndexService fileIndexService;

    @BeforeEach
    void setUp() {
        token = loginAsAdmin();
        uniquePath = "sql/controller-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @AfterEach
    void tearDown() {
        // HTTP 测试无需手动清理锁
    }

    private Long fileIdByPath(String path) {
        var entity = fileIndexService.getByPath(path);
        return entity == null ? null : entity.getId();
    }

    private String lastCommitHash(Long fileId) {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                "/api/files/history?fileId=" + fileId + "&pageNum=1&pageSize=1",
                HttpMethod.GET, new HttpEntity<>(authHeaders(token)), JsonNode.class);
        return Objects.requireNonNull(response.getBody()).path("data").path("records").get(0).path("commitHash").asText();
    }

    private void releaseLock(Long fileId) {
        LockDTO dto = new LockDTO();
        dto.setFileId(fileId);
        restTemplate.exchange("/api/files/lock/release", HttpMethod.POST,
                new HttpEntity<>(dto, authHeaders(token)), JsonNode.class);
    }

    // ==================== 鉴权与校验 ====================

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
            String filePath = uniquePath + "/locked.sql";
            // 先创建文件
            var createDto = new CreateFileDTO();
            createDto.setPath(filePath);
            restTemplate.exchange("/api/files/create", HttpMethod.POST,
                    new HttpEntity<>(createDto, authHeaders(token)), JsonNode.class);
            Long fileId = fileIdByPath(filePath);
            // create 会自动持锁，先释放以构造“未持锁”场景
            releaseLock(fileId);

            SaveFileDTO dto = new SaveFileDTO();
            dto.setFileId(fileId);
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
                    "/api/files?fileId=999999",
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
            CreateFileDTO dto = new CreateFileDTO();
            dto.setPath("sql/../../../etc/passwd");
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    "/api/files/create", HttpMethod.POST,
                    new HttpEntity<>(dto, authHeaders(token)), JsonNode.class);
            assertThat(Objects.requireNonNull(response.getBody()).path("code").asInt()).isEqualTo(30705);
        }

        @Test
        @DisplayName("绝对路径 → PATH_ILLEGAL（30705）")
        void shouldRejectAbsolutePath() {
            CreateFileDTO dto = new CreateFileDTO();
            dto.setPath("/etc/passwd");
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    "/api/files/create", HttpMethod.POST,
                    new HttpEntity<>(dto, authHeaders(token)), JsonNode.class);
            assertThat(Objects.requireNonNull(response.getBody()).path("code").asInt()).isEqualTo(30705);
        }

        @Test
        @DisplayName("反斜杠路径 → PATH_ILLEGAL（30705）")
        void shouldRejectBackslashPath() {
            CreateFileDTO dto = new CreateFileDTO();
            dto.setPath("sql\\a.sql");
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    "/api/files/create", HttpMethod.POST,
                    new HttpEntity<>(dto, authHeaders(token)), JsonNode.class);
            assertThat(Objects.requireNonNull(response.getBody()).path("code").asInt()).isEqualTo(30705);
        }

        @Test
        @DisplayName(".lanting 目录 → LANTING_DIR_FORBIDDEN（30706）")
        void shouldRejectLantingDir() {
            CreateFileDTO dto = new CreateFileDTO();
            dto.setPath(".lanting/config.json");
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    "/api/files/create", HttpMethod.POST,
                    new HttpEntity<>(dto, authHeaders(token)), JsonNode.class);
            assertThat(Objects.requireNonNull(response.getBody()).path("code").asInt()).isEqualTo(30706);
        }

        @Test
        @DisplayName(".git 目录 → LANTING_DIR_FORBIDDEN（30706）")
        void shouldRejectGitDir() {
            CreateFileDTO dto = new CreateFileDTO();
            dto.setPath(".git/config");
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    "/api/files/create", HttpMethod.POST,
                    new HttpEntity<>(dto, authHeaders(token)), JsonNode.class);
            assertThat(Objects.requireNonNull(response.getBody()).path("code").asInt()).isEqualTo(30706);
        }
    }

    // ==================== 文件类型和大小 ====================

    @Nested
    @DisplayName("文件类型和大小校验")
    class FileTypeAndSize {

        @Test
        @DisplayName(".txt 文件 → FILE_TYPE_NOT_ALLOWED（30703）")
        void shouldRejectTxtFile() {
            CreateFileDTO dto = new CreateFileDTO();
            dto.setPath(uniquePath + "/test.txt");
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    "/api/files/create", HttpMethod.POST,
                    new HttpEntity<>(dto, authHeaders(token)), JsonNode.class);
            assertThat(Objects.requireNonNull(response.getBody()).path("code").asInt()).isEqualTo(30703);
        }

        @Test
        @DisplayName("无扩展名文件 → FILE_TYPE_NOT_ALLOWED（30703）")
        void shouldRejectFileWithNoExtension() {
            CreateFileDTO dto = new CreateFileDTO();
            dto.setPath(uniquePath + "/noext");
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    "/api/files/create", HttpMethod.POST,
                    new HttpEntity<>(dto, authHeaders(token)), JsonNode.class);
            assertThat(Objects.requireNonNull(response.getBody()).path("code").asInt()).isEqualTo(30703);
        }

        @Test
        @DisplayName(".sql 文件内容等于 1MB 应通过")
        void shouldAcceptFileExactly1MB() {
            String sqlPath = uniquePath + "/exactly1mb.sql";
            CreateFileDTO createDto = new CreateFileDTO();
            createDto.setPath(sqlPath);
            restTemplate.exchange("/api/files/create", HttpMethod.POST,
                    new HttpEntity<>(createDto, authHeaders(token)), JsonNode.class);
            Long fileId = fileIdByPath(sqlPath);

            // 抢锁
            var lockBody = new LockDTO();
            lockBody.setFileId(fileId);
            restTemplate.exchange("/api/files/lock/acquire", HttpMethod.POST,
                    new HttpEntity<>(lockBody, authHeaders(token)), JsonNode.class);

            // 生成正好 1MB 的内容（ASCII 字符，每字符 1 字节）
            String content = "A".repeat(1024 * 1024);
            SaveFileDTO dto = new SaveFileDTO();
            dto.setFileId(fileId);
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
            CreateFileDTO createDto = new CreateFileDTO();
            createDto.setPath(sqlPath);
            restTemplate.exchange("/api/files/create", HttpMethod.POST,
                    new HttpEntity<>(createDto, authHeaders(token)), JsonNode.class);
            Long fileId = fileIdByPath(sqlPath);

            var lockBody = new LockDTO();
            lockBody.setFileId(fileId);
            restTemplate.exchange("/api/files/lock/acquire", HttpMethod.POST,
                    new HttpEntity<>(lockBody, authHeaders(token)), JsonNode.class);

            // 1MB + 1 字节
            String content = "A".repeat(1024 * 1024 + 1);
            SaveFileDTO dto = new SaveFileDTO();
            dto.setFileId(fileId);
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
            CreateFileDTO createDto = new CreateFileDTO();
            createDto.setPath(sqlPath);
            restTemplate.exchange("/api/files/create", HttpMethod.POST,
                    new HttpEntity<>(createDto, authHeaders(token)), JsonNode.class);
            Long fileId = fileIdByPath(sqlPath);

            var lockBody = new LockDTO();
            lockBody.setFileId(fileId);
            restTemplate.exchange("/api/files/lock/acquire", HttpMethod.POST,
                    new HttpEntity<>(lockBody, authHeaders(token)), JsonNode.class);

            SaveFileDTO dto = new SaveFileDTO();
            dto.setFileId(fileId);
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
                    "/api/files/diff?fileId=1&from=invalid-sha&to=deadbeef1234567890",
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
            CreateFileDTO createDto = new CreateFileDTO();
            createDto.setPath(filePath);
            restTemplate.exchange("/api/files/create", HttpMethod.POST,
                    new HttpEntity<>(createDto, authHeaders(token)), JsonNode.class);
            Long fileId = fileIdByPath(filePath);

            var lockBody = new LockDTO();
            lockBody.setFileId(fileId);
            restTemplate.exchange("/api/files/lock/acquire", HttpMethod.POST,
                    new HttpEntity<>(lockBody, authHeaders(token)), JsonNode.class);

            SaveFileDTO save = new SaveFileDTO();
            save.setFileId(fileId);
            save.setContent("SELECT 1");
            restTemplate.exchange("/api/files/save", HttpMethod.POST,
                    new HttpEntity<>(save, authHeaders(token)), JsonNode.class);

            CommitFileDTO commitDTO = new CommitFileDTO();
            commitDTO.setFileIds(List.of(fileId));
            commitDTO.setMessage("add");
            restTemplate.exchange("/api/files/commit", HttpMethod.POST,
                    new HttpEntity<>(commitDTO, authHeaders(token)), JsonNode.class);

            String hash = lastCommitHash(fileId);

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    "/api/files/diff?fileId=" + fileId + "&from=" + hash + "&to=" + hash,
                    HttpMethod.GET,
                    new HttpEntity<>(authHeaders(token)),
                    JsonNode.class);
            assertThat(Objects.requireNonNull(response.getBody()).path("code").asInt()).isEqualTo(0);
            assertThat(Objects.requireNonNull(response.getBody()).path("data").asText()).isEmpty();
        }
    }
}
