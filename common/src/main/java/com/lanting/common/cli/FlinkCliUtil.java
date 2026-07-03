package com.lanting.common.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Flink CLI 工具类。
 * <p>
 * 封装 Flink 命令行调用逻辑，如版本检测、提交作业等。
 * 与框架无关，不依赖 Spring。
 *
 * @author wangzhao
 */
public final class FlinkCliUtil {

    private FlinkCliUtil() {
    }

    /**
     * 检测 Flink 版本号。
     * <p>
     * 执行 {@code {flinkHome}/bin/flink --version}，
     * 输出示例（前面可能有多行 SLF4J 日志）：
     * <pre>{@code
     * SLF4J: Class path contains multiple SLF4J bindings.
     * ...
     * Version: 1.19.3, Commit ID: 4105f8d
     * }</pre>
     *
     * @param flinkHome Flink 安装路径
     * @return 版本号字符串，如 "1.19.3"
     * @throws FlinkCliException 执行失败或输出解析失败时
     */
    public static String checkVersion(String flinkHome) {
        String command = flinkHome + "/bin/flink";
        try {
            ProcessBuilder pb = new ProcessBuilder(command, "--version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("Version:")) {
                        String version = line
                                .substring("Version:".length())
                                .trim()
                                .split(",")[0]
                                .trim();
                        process.waitFor();
                        return version;
                    }
                }
            }
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FlinkCliException("Flink 版本检测失败，flinkHome=" + flinkHome, e);
        }
        throw new FlinkCliException("Flink 版本检测失败，无法解析输出，flinkHome=" + flinkHome);
    }
}
