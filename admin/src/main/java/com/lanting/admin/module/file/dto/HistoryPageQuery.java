package com.lanting.admin.module.file.dto;

import com.lanting.admin.common.page.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文件历史分页查询参数。
 *
 * @author wangzhao
 */
@Schema(description = "文件历史分页查询参数")
@Data
@EqualsAndHashCode(callSuper = true)
public class HistoryPageQuery extends PageQuery {

    /**
     * 文件相对路径，为空时查询整个仓库历史。
     */
    @NotNull
    @Schema(description = "文件相对路径，为空时查询整个仓库历史")
    private String path;
}
