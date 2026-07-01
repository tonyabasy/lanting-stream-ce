package com.lanting.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lanting.admin.module.user.dto.LoginDTO;
import com.lanting.admin.module.user.mapper.UserMapper;
import com.lanting.admin.module.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * Controller 集成测试基类。
 * <p>
 * 启动完整 Spring 上下文，使用测试数据库（application-test.yml）。
 * 提供登录工具方法和带鉴权请求头的构建方法，子类直接使用。
 *
 * @author wangzhao
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserService userService;

    @Autowired
    protected UserMapper userMapper;

    @Autowired
    protected BCryptPasswordEncoder passwordEncoder;

    /**
     * 每个测试前重置 admin 密码为 "admin123"，保证登录测试的可重复性。
     * Flyway 初始化的 admin 密码是 PLACEHOLDER，需要在测试前替换为真实哈希。
     */
    @BeforeEach
    void resetAdminPassword() {
        userMapper.updateById(buildAdminWithPassword("admin123"));
    }

    /**
     * 以 admin 身份登录，返回 token。
     */
    protected String loginAsAdmin() {
        return login("admin", "admin123");
    }

    /**
     * 登录并返回 token，登录失败时抛出异常让测试快速失败。
     */
    protected String login(String username, String password) {
        LoginDTO dto = new LoginDTO();
        dto.setUsername(username);
        dto.setPassword(password);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/auth/login",
                new HttpEntity<>(dto, jsonHeaders()),
                String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("登录失败，无法执行测试: " + response.getBody());
        }
        try {
            return objectMapper.readTree(response.getBody())
                    .path("data").path("tokenInfo").path("token").asText();
        } catch (Exception e) {
            throw new IllegalStateException("解析 token 失败", e);
        }
    }

    /**
     * 构建带 token 的请求头。
     */
    protected HttpHeaders authHeaders(String token) {
        HttpHeaders headers = jsonHeaders();
        headers.set("lanting-token", token);
        return headers;
    }

    /**
     * 构建 JSON 请求头（不带 token）。
     */
    protected HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    /**
     * 构建带密码的 admin 用户对象，用于重置密码。
     */
    private com.lanting.admin.module.user.entity.UserEntity buildAdminWithPassword(String rawPassword) {
        com.lanting.admin.module.user.entity.UserEntity user =
                new com.lanting.admin.module.user.entity.UserEntity();
        user.setId(1L);
        user.setPassword(passwordEncoder.encode(rawPassword));
        return user;
    }
}
