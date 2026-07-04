package com.lanting.admin.module.user.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.lanting.admin.BaseIntegrationTest;
import com.lanting.admin.module.user.dto.LoginDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AuthController 集成测试。
 * <p>
 * 验证：路由、参数校验、业务错误码、响应体结构（敏感字段不暴露）、鉴权拦截。
 *
 * @author wangzhao
 */
class AuthControllerTest extends BaseIntegrationTest {

    // ==================== POST /api/auth/login ====================

    @Test
    @DisplayName("用户名为空时登录返回 400")
    void login_shouldReturn400_whenUsernameBlank() throws Exception {
        LoginDTO dto = new LoginDTO();
        dto.setUsername("");
        dto.setPassword("admin123");

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/auth/login",
                new HttpEntity<>(dto, jsonHeaders()),
                String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals(10001, body.path("code").asInt());
    }

    @Test
    @DisplayName("密码为空时登录返回 400")
    void login_shouldReturn400_whenPasswordBlank() throws Exception {
        LoginDTO dto = new LoginDTO();
        dto.setUsername("admin");
        dto.setPassword("");

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/auth/login",
                new HttpEntity<>(dto, jsonHeaders()),
                String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals(10001, body.path("code").asInt());
    }

    @Test
    @DisplayName("密码错误时登录返回 400")
    void login_shouldReturn400_whenPasswordWrong() throws Exception {
        LoginDTO dto = new LoginDTO();
        dto.setUsername("admin");
        dto.setPassword("wrongpassword");

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/auth/login",
                new HttpEntity<>(dto, jsonHeaders()),
                String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals(30103, body.path("code").asInt());
    }

    @Test
    @DisplayName("用户不存在时登录返回 400")
    void login_shouldReturn400_whenUserNotFound() throws Exception {
        LoginDTO dto = new LoginDTO();
        dto.setUsername("nonexistent");
        dto.setPassword("admin123");

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/auth/login",
                new HttpEntity<>(dto, jsonHeaders()),
                String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals(30103, body.path("code").asInt());
    }

    @Test
    @DisplayName("合法登录返回 200 且不暴露敏感字段")
    void login_shouldReturn200_andNotExposePassword_whenValid() throws Exception {
        LoginDTO dto = new LoginDTO();
        dto.setUsername("admin");
        dto.setPassword("admin123");

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/auth/login",
                new HttpEntity<>(dto, jsonHeaders()),
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode data = objectMapper.readTree(response.getBody()).path("data");
        assertEquals("admin", data.path("username").asText());
        // 敏感字段不能出现在响应中
        assertTrue(data.path("password").isMissingNode());
        assertTrue(data.path("authSource").isMissingNode());
        // token 信息正常返回
        assertFalse(data.path("tokenInfo").path("token").asText().isBlank());
    }

    // ==================== GET /api/auth/current ====================

    @Test
    @DisplayName("未登录获取当前用户返回 401")
    void current_shouldReturn401_whenNotLoggedIn() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/auth/current", String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals(20001, body.path("code").asInt());
    }

    @Test
    @DisplayName("已登录获取当前用户返回 200")
    void current_shouldReturn200_whenLoggedIn() throws Exception {
        String token = loginAsAdmin();

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/auth/current",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode data = objectMapper.readTree(response.getBody()).path("data");
        assertEquals("admin", data.path("username").asText());
        assertTrue(data.path("password").isMissingNode());
    }

    // ==================== POST /api/auth/logout ====================

    @Test
    @DisplayName("登出返回 200")
    void logout_shouldReturn200() {
        String token = loginAsAdmin();

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/auth/logout",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(token)),
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
}
