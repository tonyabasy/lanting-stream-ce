package com.lanting.admin.module.file.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 发布级回滚预检 VO。
 *
 * @author wangzhao
 */
@Data
@Schema(description = "发布级回滚预检结果")
public class RollbackCheckVO {

    /** 当前被他人锁定的文件列表 */
    @Schema(description = "当前被他人锁定的文件列表")
    private List<LockedFileVO> lockedFiles;
}
