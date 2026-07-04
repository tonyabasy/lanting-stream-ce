package com.lanting.admin.common.i18n;

import com.lanting.admin.common.result.CommonResultCode;
import com.lanting.admin.common.result.ResultCode;
import com.lanting.admin.module.cluster.result.ClusterResultCode;
import com.lanting.admin.module.test.TestResultCode;
import com.lanting.admin.module.user.result.UserResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 国际化资源文件一致性测试。
 * <p>
 * 确保 {@link ResultCode} 枚举中的错误码与 {@code i18n/messages*.properties} 资源文件保持同步：
 * <ul>
 *   <li>默认中文资源文件必须包含所有错误码；</li>
 *   <li>默认中文文案必须与 {@link ResultCode#getMessage()} 一致；</li>
 *   <li>英文资源文件必须包含所有错误码。</li>
 * </ul>
 *
 * @author wangzhao
 */
@DisplayName("国际化资源文件一致性测试")
class I18nConsistencyTest {

    private static final String MESSAGES_DEFAULT = "i18n/messages.properties";
    private static final String MESSAGES_EN = "i18n/messages_en_US.properties";

    @SuppressWarnings("unchecked")
    private static final List<Class<? extends ResultCode>> RESULT_CODE_ENUMS = List.of(
            CommonResultCode.class,
            UserResultCode.class,
            ClusterResultCode.class,
            TestResultCode.class
    );

    @Test
    @DisplayName("默认中文资源文件覆盖所有 ResultCode 且文案一致")
    void defaultMessagesShouldCoverAllResultCodes() throws IOException {
        Properties properties = loadProperties(MESSAGES_DEFAULT);

        for (ResultCode resultCode : allResultCodes()) {
            String code = String.valueOf(resultCode.getCode());
            assertTrue(properties.containsKey(code),
                    "默认中文资源文件缺少错误码: " + code + " (" + resultCode.getMessage() + ")");
            assertEquals(resultCode.getMessage(), properties.getProperty(code),
                    "错误码 " + code + " 在枚举与默认中文资源文件中的文案不一致");
        }
    }

    @Test
    @DisplayName("英文资源文件覆盖所有 ResultCode 且文案非空")
    void englishMessagesShouldCoverAllResultCodes() throws IOException {
        Properties properties = loadProperties(MESSAGES_EN);

        for (ResultCode resultCode : allResultCodes()) {
            String code = String.valueOf(resultCode.getCode());
            assertTrue(properties.containsKey(code),
                    "英文资源文件缺少错误码: " + code + " (" + resultCode.getMessage() + ")");
            assertFalse(properties.getProperty(code).isBlank(),
                    "英文资源文件中错误码 " + code + " 的文案不能为空");
        }
    }

    private static List<ResultCode> allResultCodes() {
        List<ResultCode> resultCodes = new ArrayList<>();
        for (Class<? extends ResultCode> enumClass : RESULT_CODE_ENUMS) {
            ResultCode[] constants = enumClass.getEnumConstants();
            if (constants != null) {
                for (ResultCode code : constants) {
                    // SUCCESS(0) 不是错误码，不需要出现在错误文案资源文件中
                    if (code.getCode() != 0) {
                        resultCodes.add(code);
                    }
                }
            }
        }
        return resultCodes;
    }

    private static Properties loadProperties(String path) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = I18nConsistencyTest.class.getClassLoader().getResourceAsStream(path)) {
            assertTrue(input != null, "资源文件不存在: " + path);
            // 必须显式指定 UTF-8，Properties.load(InputStream) 默认使用 ISO-8859-1
            properties.load(new InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8));
        }
        return properties;
    }
}
