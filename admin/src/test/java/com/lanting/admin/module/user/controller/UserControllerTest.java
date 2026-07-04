package com.lanting.admin.module.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.lanting.admin.BaseIntegrationTest;
import com.lanting.admin.module.user.dto.CreateUserDTO;
import com.lanting.admin.module.user.dto.ResetPasswordDTO;
import com.lanting.admin.module.user.entity.UserEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.Collections;
import java.util.List;

import static com.lanting.admin.module.user.result.UserResultCode.SUPER_ADMIN_PROTECTED;
import static org.junit.jupiter.api.Assertions.*;

/**
 * UserController 集成测试。
 * <p>
 * 验证：鉴权拦截（未登录 401）、权限拦截（非超管 403）、参数校验（400）、正常业务流程（200）。
 *
 * @author wangzhao
 */
class UserControllerTest extends BaseIntegrationTest {

    private static final String TEST_USERNAME = "testuser_ctrl";

    /**
     * 每个测试后清理创建的测试用户，保证测试隔离。
     */
    @AfterEach
    void cleanup() {
        deleteTestUserPhysically();
    }

    // ==================== GET /api/users（用户列表） ====================

    @Test
    @DisplayName("未登录获取用户列表返回 401")
    void listUsers_shouldReturn401_whenNotLoggedIn() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/users", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @DisplayName("已登录获取用户列表返回 200")
    void listUsers_shouldReturn200_whenLoggedIn() throws Exception {
        String token = loginAsAdmin();

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/users",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode data = objectMapper.readTree(response.getBody()).path("data");
        assertTrue(data.path("total").asLong() >= 1);
        assertTrue(data.path("records").isArray());
    }

    // ==================== POST /api/users（创建用户） ====================

    @Test
    @DisplayName("未登录创建用户返回 401")
    void createUser_shouldReturn401_whenNotLoggedIn() throws Exception {
        CreateUserDTO dto = buildCreateDTO();

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/users",
                new HttpEntity<>(dto, jsonHeaders()),
                String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @DisplayName("非超管创建用户返回 403")
    void createUser_shouldReturn403_whenNotSuperAdmin() throws Exception {
        // 先用超管创建一个普通用户
        String adminToken = loginAsAdmin();
        createTestUser(adminToken);

        // 用普通用户登录后尝试创建用户
        String userToken = login(TEST_USERNAME, "Password123");
        CreateUserDTO dto = new CreateUserDTO();
        dto.setUsername("another_user");
        dto.setPassword("Password123");

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/users",
                HttpMethod.POST,
                new HttpEntity<>(dto, authHeaders(userToken)),
                String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals(20002, body.path("code").asInt());
    }

    @Test
    @DisplayName("用户名为空时创建用户返回 400")
    void createUser_shouldReturn400_whenUsernameBlank() throws Exception {
        String token = loginAsAdmin();
        CreateUserDTO dto = new CreateUserDTO();
        dto.setUsername("");
        dto.setPassword("Password123");

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/users",
                HttpMethod.POST,
                new HttpEntity<>(dto, authHeaders(token)),
                String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals(10001, body.path("code").asInt());
    }

    @Test
    @DisplayName("用户名重复时创建用户返回 400")
    void createUser_shouldReturn400_whenUsernameDuplicate() throws Exception {
        String token = loginAsAdmin();
        CreateUserDTO dto = new CreateUserDTO();
        dto.setUsername("admin");  // admin 已存在
        dto.setPassword("Password123");

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/users",
                HttpMethod.POST,
                new HttpEntity<>(dto, authHeaders(token)),
                String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals(30102, body.path("code").asInt());
    }

    @Test
    @DisplayName("合法创建用户返回 200 且不暴露密码")
    void createUser_shouldReturn200_andNotExposePassword_whenValid() throws Exception {
        String token = loginAsAdmin();
        ResponseEntity<String> response = createTestUser(token);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode data = objectMapper.readTree(response.getBody()).path("data");
        assertEquals(TEST_USERNAME, data.path("username").asText());
        assertTrue(data.path("password").isMissingNode());
        assertTrue(data.path("authSource").isMissingNode());
    }

    // ==================== DELETE /api/users/{id}（删除用户） ====================

    @Test
    @DisplayName("删除自己返回 403")
    void deleteUser_shouldReturn403_whenDeletingSelf() throws Exception {
        String token = loginAsAdmin();
        long adminId = objectMapper.readTree(
                restTemplate.exchange("/api/auth/current", HttpMethod.GET,
                        new HttpEntity<>(authHeaders(token)), String.class).getBody())
                .path("data").path("id").asLong();

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/users/" + adminId,
                HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)),
                String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals(SUPER_ADMIN_PROTECTED.getCode(), body.path("code").asInt());
    }

    @Test
    @DisplayName("删除受保护管理员返回 403")
    void deleteUser_shouldReturn403_whenDeletingProtectedAdmin() throws Exception {
        String token = loginAsAdmin();

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/users/1",  // id=1 是受保护的 admin
                HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)),
                String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals(30106, body.path("code").asInt());
    }

    // ==================== PUT /api/users/{id}/password（重置密码） ====================

    @Test
    @DisplayName("超管重置密码返回 200 且新密码生效")
    void resetPassword_shouldReturn200_whenSuperAdmin() throws Exception {
        String adminToken = loginAsAdmin();
        createTestUser(adminToken);

        var user = userService.getUserByName(TEST_USERNAME);
        ResetPasswordDTO dto = new ResetPasswordDTO();
        dto.setNewPassword("NewPassword456");

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/users/" + user.getId() + "/password",
                HttpMethod.PUT,
                new HttpEntity<>(dto, authHeaders(adminToken)),
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        // 验证新密码生效
        String newToken = login(TEST_USERNAME, "NewPassword456");
        assertFalse(newToken.isBlank());
    }

    // ==================== 工具方法 ====================

    private ResponseEntity<String> createTestUser(String adminToken) {
        deleteTestUserPhysically();

        CreateUserDTO dto = buildCreateDTO();
        return restTemplate.exchange(
                "/api/users",
                HttpMethod.POST,
                new HttpEntity<>(dto, authHeaders(adminToken)),
                String.class);
    }

    /**
     * 物理删除测试用户
     */
    private void deleteTestUserPhysically() {
        List<UserEntity> testUser = userService.listIncludeDeleted(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, TEST_USERNAME));
        if (testUser != null && !testUser.isEmpty()) {
            userService.removePhysicallyByIds(Collections.singleton(testUser.getFirst().getId()));
        }
    }

    private CreateUserDTO buildCreateDTO() {
        CreateUserDTO dto = new CreateUserDTO();
        dto.setUsername(TEST_USERNAME);
        dto.setPassword("Password123");
        dto.setNickname("Test User");
        return dto;
    }
}
