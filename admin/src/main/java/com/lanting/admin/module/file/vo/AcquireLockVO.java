package com.lanting.admin.module.file.vo;

import com.lanting.admin.module.file.service.FileLockService;
import com.lanting.admin.module.file.service.LockInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 抢锁结果 VO。
 *
 * @author wangzhao
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
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

    public static AcquireLockVO success(LockInfo preLockInfo) {
        return of(true, preLockInfo);
    }

    public static AcquireLockVO failed(LockInfo preLockInfo) {
        return of(false, preLockInfo);
    }

    public static AcquireLockVO of(boolean acquired, LockInfo preLockInfo) {
        String previousHolder = preLockInfo == null ? null : preLockInfo.getHolder();
        Long previousHolderAt = preLockInfo == null ? null : preLockInfo.getLockedAt();
        return new AcquireLockVO(acquired, previousHolder, previousHolderAt);
    }
}
