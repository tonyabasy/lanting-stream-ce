package com.lanting.admin.common.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * 将 {@link Long} 值序列化为 JSON 字符串（例如将时间戳转为十进制字符串）。
 *
 * @author wangzhao
 * @since 2026-03-13
 */
public class LongToStringSerializer extends JsonSerializer<Long> {

    @Override
    public void serialize(Long value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        gen.writeString(value.toString());
    }
}
