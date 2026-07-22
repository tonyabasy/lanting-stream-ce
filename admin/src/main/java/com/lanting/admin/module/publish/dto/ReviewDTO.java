package com.lanting.admin.module.publish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * CR 评审请求。
 *
 * @author wangzhao
 */
@Data
@Schema(description = "Review 请求")
public class ReviewDTO {

    @NotBlank(message = "commitHash 不能为空")
    @Schema(description = "评审所针对的 commit hash，须与文件当前 latest_commit_hash 一致")
    private String commitHash;

    @NotBlank(message = "result 不能为空")
    @Schema(description = "评审结果 APPROVED / REJECTED")
    private String result;

    @Schema(description = "评审意见")
    private String comment;
}
