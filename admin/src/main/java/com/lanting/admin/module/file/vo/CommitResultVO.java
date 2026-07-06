package com.lanting.admin.module.file.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 提交结果 VO。
 *
 * @author wangzhao
 */
@Data
@Schema(description = "提交结果")
public class CommitResultVO {

    /** 本次 commit SHA，committed 为空时为 null */
    @Schema(description = "本次 commit SHA")
    private String commitHash;

    /** 实际提交的文件列表 */
    @Schema(description = "实际提交的文件列表")
    private List<String> committed;

    /** 被跳过的文件列表（他人持锁） */
    @Schema(description = "被跳过的文件列表")
    private List<String> skipped;
}
