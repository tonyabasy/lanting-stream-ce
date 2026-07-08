package com.lanting.admin.module.file.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.lanting.admin.BaseIntegrationTest;
import org.junit.jupiter.api.*;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FileSystemAdminController HTTP 接口集成测试。覆盖 reconcile 和 status 管理接口。
 *
 * @author wangzhao
 */
@DisplayName("FileSystemAdminController HTTP 集成测试")
class FileSystemAdminControllerTest extends BaseIntegrationTest {

    private String token;

    @BeforeEach
    void setUp() {
        token = loginAsAdmin();
    }

    // ==================== reconcile ====================

    @Nested
    @DisplayName("reconcile 接口")
    class ReconcileApi {

        @Test
        @DisplayName("手动触发一致性校验 → 返回完整报告")
        void shouldReturnReconcileReport() {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    "/api/admin/fs/reconcile",
                    HttpMethod.POST,
                    new HttpEntity<>(authHeaders(token)),
                    JsonNode.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode data = response.getBody().path("data");
            System.out.println(data.toPrettyString());
            assertThat(data.has("total")).isTrue();
            assertThat(data.has("unindexedFiles")).isTrue();
            assertThat(data.has("unindexedFolders")).isTrue();
            assertThat(data.has("staleFiles")).isTrue();
            assertThat(data.has("staleFolders")).isTrue();
            assertThat(data.has("mtimeMismatches")).isTrue();
        }

        @Test
        @DisplayName("未登录访问 → 401")
        void shouldReturn401WhenUnauthorized() {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    "/api/admin/fs/reconcile",
                    HttpMethod.POST,
                    new HttpEntity<>(jsonHeaders()),
                    JsonNode.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    // ==================== status ====================

    @Nested
    @DisplayName("status 接口")
    class StatusApi {

        @Test
        @DisplayName("查询索引总记录数")
        void shouldReturnIndexStatus() {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    "/api/admin/fs/status",
                    HttpMethod.GET,
                    new HttpEntity<>(authHeaders(token)),
                    JsonNode.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode data = response.getBody().path("data");
            assertThat(data.has("total")).isTrue();
            assertThat(data.path("total").asLong()).isGreaterThanOrEqualTo(0);
        }
    }
}
