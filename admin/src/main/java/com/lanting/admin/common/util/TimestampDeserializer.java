package com.lanting.admin.common.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 将格式化的日期时间字符串反序列化为毫秒时间戳 {@link Long}（用于 JSON 请求体）。
 *
 * @author wangzhao
 * @since 2026-03-13
 */
public class TimestampDeserializer extends JsonDeserializer<Long> {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern(Constants.STANDARD_DATE_TIME_FORMAT);

    @Override
    public Long deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
        String text = p.getText();
        if (text == null || text.isBlank()) {
            return null;
        }
        LocalDateTime dateTime = LocalDateTime.parse(text.trim(), FORMATTER);
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
