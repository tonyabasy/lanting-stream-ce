package com.lanting.admin.module.file.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件锁状态 VO。
 *
 * @author wangzhao
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "文件锁状态")
public class LockVO {

    /** 持锁人 username，未锁定为 null */
    @Schema(description = "持锁人 username，未锁定为 null")
    private String lockedBy;

    /** 抢锁时间戳（毫秒），未锁定为 null */
    @Schema(description = "抢锁时间戳（毫秒）")
    private Long lockedAt;
}
