package com.lanting.admin.module.publish.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lanting.admin.common.basic.BasicEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 发布评审记录表（lanting_file_review）。
 * 绑定 (file_id, commit_hash)，可多条，不阻断发布，仅作审计日志。
 *
 * @author wangzhao
 */
@Getter
@Setter
@TableName("lanting_file_review")
public class ReviewEntity extends BasicEntity {

    @Schema(description = "文件 ID")
    private Long fileId;

    @Schema(description = "评审所针对的 commit hash")
    private String commitHash;

    @Schema(description = "评审人")
    private String reviewer;

    @Schema(description = "评审意见")
    private String comment;

    @Schema(description = "评审结果 APPROVED / REJECTED")
    private String result;
}
