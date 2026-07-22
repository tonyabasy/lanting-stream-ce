package com.lanting.admin.module.publish.vo;

import com.lanting.admin.module.publish.entity.ReviewEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * CR 评审视图。
 *
 * @author wangzhao
 */
@Data
@Schema(description = "Review 记录")
public class ReviewVO {

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

    @Schema(description = "创建时间（毫秒）")
    private Long createTime;

    public static ReviewVO of(ReviewEntity entity) {
        ReviewVO vo = new ReviewVO();
        vo.setFileId(entity.getFileId());
        vo.setCommitHash(entity.getCommitHash());
        vo.setReviewer(entity.getReviewer());
        vo.setComment(entity.getComment());
        vo.setResult(entity.getResult());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}
