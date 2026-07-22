package com.lanting.admin.module.publish.vo;

import com.lanting.admin.module.publish.entity.PublishEntity;
import com.lanting.admin.module.publish.entity.PublishFileEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 发布批次视图。
 *
 * @author wangzhao
 */
@Data
@Schema(description = "发布批次")
public class PublishVO {

    @Schema(description = "批次 ID")
    private String publishId;

    @Schema(description = "批次展示名")
    private String displayName;

    @Schema(description = "发布人")
    private String publishedBy;

    @Schema(description = "创建时间（毫秒）")
    private Long createTime;

    @Schema(description = "本批次包含的文件（含各自 commit SHA）")
    private List<PublishFileItem> files;

    // 以下字段为兼容遗留 GitFileService.publish（基于 git tag）保留，v2 流程不使用，恒为 null
    @Deprecated
    @Schema(description = "遗留字段：git tag 名（v2 不使用）")
    private String tagName;

    @Deprecated
    @Schema(description = "遗留字段：tag 对应 commit hash（v2 不使用）")
    private String commitHash;

    @Deprecated
    @Schema(description = "遗留字段：发布时间戳（v2 不使用）")
    private Long timestamp;

    @Data
    @Schema(description = "发布批次内的文件项")
    public static class PublishFileItem {
        @Schema(description = "文件 ID")
        private Long fileId;

        @Schema(description = "文件名")
        private String fileName;

        @Schema(description = "定格的 commit SHA")
        private String commitHash;

        public static PublishFileItem of(PublishFileEntity entity) {
            PublishFileItem item = new PublishFileItem();
            item.setFileId(entity.getFileId());
            item.setFileName(entity.getFileName());
            item.setCommitHash(entity.getCommitHash());
            return item;
        }
    }

    public static PublishVO of(PublishEntity entity, List<PublishFileItem> files) {
        PublishVO vo = new PublishVO();
        vo.setPublishId(entity.getId());
        vo.setDisplayName(entity.getDisplayName());
        vo.setPublishedBy(entity.getPublishedBy());
        vo.setCreateTime(entity.getCreateTime());
        vo.setFiles(files);
        return vo;
    }

    public static PublishVO of(PublishEntity entity) {
        return of(entity, null);
    }
}
