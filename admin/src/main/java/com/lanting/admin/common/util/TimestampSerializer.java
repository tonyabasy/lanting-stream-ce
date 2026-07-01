package com.lanting.admin.common.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 将毫秒时间戳 {@link Long} 序列化为格式化日期时间字符串（用于 JSON 响应）。
 *
 * @author wangzhao
 * @since 2026-03-13
 */
public class TimestampSerializer extends JsonSerializer<Long> {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern(Constants.STANDARD_DATE_TIME_FORMAT);

    @Override
    public void serialize(Long value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        LocalDateTime dateTime =
                LocalDateTime.ofInstant(Instant.ofEpochMilli(value), ZoneId.systemDefault());
        gen.writeString(FORMATTER.format(dateTime));
    }
}
