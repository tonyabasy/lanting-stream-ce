package com.lanting.admin.module.user.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.lanting.admin.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import static com.lanting.admin.module.test.TestResultCode.TEST_MESSAGE_FORMAT;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * TestController 集成测试。
 *
 * @author wangzhao
 */
@DisplayName("TestController 集成测试")
class TestControllerTest extends BaseIntegrationTest {

    @Test
    @DisplayName("中文请求下业务异常返回已填充占位符的中文消息")
    void businessException_shouldReturnChineseMessageWithFilledArgs_whenAcceptLanguageIsZhCN() throws Exception {
        ResponseEntity<String> response = restTemplate.exchange(
                "/test/business-exception",
                HttpMethod.GET,
                new HttpEntity<>(languageHeaders("zh-CN")),
                String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals(TEST_MESSAGE_FORMAT.getCode(), body.path("code").asInt());
        assertEquals("测试参数 Alice 和 42", body.path("message").asText());
    }

    @Test
    @DisplayName("英文请求下业务异常返回已填充占位符的英文消息")
    void businessException_shouldReturnEnglishMessageWithFilledArgs_whenAcceptLanguageIsEnUS() throws Exception {
        ResponseEntity<String> response = restTemplate.exchange(
                "/test/business-exception",
                HttpMethod.GET,
                new HttpEntity<>(languageHeaders("en-US")),
                String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals(TEST_MESSAGE_FORMAT.getCode(), body.path("code").asInt());
        assertEquals("Test parameters Alice and 42", body.path("message").asText());
    }

    private HttpHeaders languageHeaders(String language) {
        HttpHeaders headers = jsonHeaders();
        headers.set("Accept-Language", language);
        return headers;
    }
}
