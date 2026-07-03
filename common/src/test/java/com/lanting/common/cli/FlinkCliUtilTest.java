package com.lanting.common.cli;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link FlinkCliUtil} 单元测试。
 *
 * @author wangzhao
 */
class FlinkCliUtilTest {

    private static final String FLINK_HOME = "/Users/wangzhao/.flink-dist/flink-2.1.1";

    @Test
    @DisplayName("检测 Flink 版本号 — 正常路径")
    @Disabled("需要本地 Flink 环境，CI 不执行")
    void checkVersion_shouldReturnVersion() {
        String version = FlinkCliUtil.checkVersion(FLINK_HOME);
        assertNotNull(version);
        assertFalse(version.isBlank());
        // 版本号格式如 "2.1.1"
        assertTrue(version.matches("2.1.1"), "期望版本号格式 x.y.z，实际: " + version);
    }

    @Test
    @DisplayName("检测 Flink 版本号 — 路径不存在时抛异常")
    void checkVersion_shouldThrow_whenFlinkHomeInvalid() {
        assertThrows(FlinkCliException.class, () ->
                FlinkCliUtil.checkVersion("/not/exist/flink"));
    }
}
