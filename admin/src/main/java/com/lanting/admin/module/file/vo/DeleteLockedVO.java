package com.lanting.admin.module.file.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 删除文件夹时被锁定文件 VO。
 *
 * @author wangzhao
 */
@Data
@Schema(description = "删除文件夹锁冲突结果")
public class DeleteLockedVO {

    /** 被他人锁定的文件列表 */
    @Schema(description = "被他人锁定的文件列表")
    private List<LockedFileVO> lockedFiles;
}
