package com.lanting.admin.module.file.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 抢锁结果 VO。
 *
 * @author wangzhao
 */
@Data
@Schema(description = "抢锁结果")
public class AcquireLockVO {

    /** 是否抢锁成功（软锁下恒为 true） */
    @Schema(description = "是否抢锁成功")
    private boolean acquired;

    /** 上一个持锁人 username，null 表示之前无人持锁 */
    @Schema(description = "上一个持锁人 username")
    private String previousHolder;

    /** 上一个持锁人抢锁时间戳（毫秒） */
    @Schema(description = "上一个持锁人抢锁时间戳（毫秒）")
    private Long previousHolderAt;
}
