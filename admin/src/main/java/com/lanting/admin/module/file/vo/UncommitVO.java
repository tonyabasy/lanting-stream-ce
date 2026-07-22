package com.lanting.admin.module.file.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Collections;
import java.util.List;

/**
 * 文件未提交变更明细。
 *
 * @author wangzhao
 */
@Data
@Schema(description = "文件未提交变更")
public class UncommitVO {

    @Schema(description = "已跟踪文件的未暂存修改路径")
    private List<String> modified = Collections.emptyList();

    @Schema(description = "从未提交过的新文件路径")
    private List<String> untracked = Collections.emptyList();

    /**
     * 是否完全无变更（无需 commit）。
     */
    public boolean isEmpty() {
        return modified.isEmpty() && untracked.isEmpty();
    }
}
