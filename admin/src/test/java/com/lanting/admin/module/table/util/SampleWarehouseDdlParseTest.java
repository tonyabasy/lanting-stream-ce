package com.lanting.admin.module.table.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 校验 docs/sample-warehouse/tables/ 下所有 DDL 都能通过 {@link FlinkSqlParser} 解析。
 *
 * <p>样本 DDL 是仓库自带的数仓示例表，前端 TableEditor 依赖后端 {@link FlinkSqlParser}
 * 解析这些文件后才能在表单/文本模式打开，因此任何一个样本 DDL 解析失败都意味着
 * 对应表无法被编辑器处理。断言标准：parse 不抛异常即可（不校验字段、分区等元数据细节）。
 *
 * <p>测试遍历目录下全部 {@code *.ddl}（而非硬编码文件清单），新增样本自动被覆盖。
 */
class SampleWarehouseDdlParseTest {

    /** 样本根目录，相对测试进程工作目录的多候选路径（surefire 默认 workingDirectory 为 admin/） */
    private static final String RELATIVE_PATH = "docs/sample-warehouse/tables";

    @Test
    void allSampleWarehouseDdlShouldParseWithoutError() throws IOException {
        Path base = locateBase();
        List<Path> ddlFiles;
        try (var stream = Files.walk(base)) {
            ddlFiles = stream
                    .filter(p -> p.toString().endsWith(".ddl"))
                    .sorted()
                    .toList();
        }
        assertThat(ddlFiles)
                .as("样本目录 %s 下应存在 .ddl 文件", base)
                .isNotEmpty();

        List<String> failures = new ArrayList<>();
        for (Path file : ddlFiles) {
            String ddl = Files.readString(file, StandardCharsets.UTF_8);
            String relative = base.relativize(file).toString();
            try {
                FlinkSqlParser.parseCreateTable(ddl);
            } catch (Exception e) {
                failures.add(relative + " 解析失败: " + e.getMessage());
            }
        }

        assertThat(failures)
                .as("以下 sample-warehouse DDL 无法通过 FlinkSqlParser 解析（共 %d 个文件）", ddlFiles.size())
                .isEmpty();
    }

    private static Path locateBase() throws IOException {
        for (String prefix : List.of("..", ".")) {
            Path candidate = Path.of(prefix, RELATIVE_PATH).toAbsolutePath().normalize();
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "找不到 docs/sample-warehouse/tables（cwd=" + Path.of("").toAbsolutePath().normalize() + "）");
    }
}
