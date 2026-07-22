package com.lanting.admin.module.publish.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 待发布候选视图。
 *
 * @author wangzhao
 */
@Data
@Schema(description = "待发布候选")
public class CommitVO {

    @Schema(description = "文件 ID")
    private Long fileId;

    @Schema(description = "文件名")
    private String name;

    @Schema(description = "文件当前 latest_commit_hash")
    private String commitHash;

    @Schema(description = "动态 CR 状态：NONE / APPROVED / REJECTED")
    private String status;
}
