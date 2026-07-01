package com.lanting.admin.common.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lanting.admin.common.exception.JsonFormatException;

/**
 * JSON 工具类，使用全局共享的 {@link ObjectMapper}，保证 Jackson 设置在整个应用中保持一致。
 *
 * @author wangzhao
 * @since 2025-12-24
 */
public class JACKSON {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    public static <T> String toJSONString(T object) {
        try {
            return MAPPER.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new JsonFormatException(e);
        }
    }

    public static <T> T parseObject(String json, Class<T> clazz) {
        try {
            return MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new JsonFormatException(e);
        }
    }

    public static <T> T parseObject(String json, TypeReference<T> typeReference) {
        try {
            return MAPPER.readValue(json, typeReference);
        } catch (JsonProcessingException e) {
            throw new JsonFormatException(e);
        }
    }
}
