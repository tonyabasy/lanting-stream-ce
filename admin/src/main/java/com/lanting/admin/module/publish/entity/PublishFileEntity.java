package com.lanting.admin.module.publish.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lanting.admin.common.basic.BasicEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 发布文件生命周期表（lanting_publish_file）。
 * 候选态：publish_id = ''，commit_hash = ''；发布时定格 commit_hash 并翻 PUBLISHED。
 * 同一 file_id 同一时刻仅一条 COMMITTED；CANCELED 后重提流水追加新行。
 *
 * @author wangzhao
 */
@Getter
@Setter
@TableName("lanting_publish_file")
public class PublishFileEntity extends BasicEntity {

    /**
     * 已提交待发布
     */
    public static final String STATUS_COMMITTED = "COMMITTED";
    /**
     * 已发布
     */
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    /**
     * 已取消
     */
    public static final String STATUS_CANCELED = "CANCELED";

    @Schema(description = "所属发布批次 ID（UUID，空串表示尚未发布）")
    private String publishId;

    @Schema(description = "文件 ID")
    private Long fileId;

    @Schema(description = "文件名")
    private String fileName;

    @Schema(description = "发布/取消时的 commit hash（待发布态为空，发布时写入）")
    private String commitHash;

    @Schema(description = "状态 COMMITTED / PUBLISHED / CANCELED")
    private String status;

    @Schema(description = "创建人")
    private String createdBy;

    @Schema(description = "更新人")
    private String updatedBy;
}
