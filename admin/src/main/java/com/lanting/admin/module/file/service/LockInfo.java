package com.lanting.admin.module.file.service;

import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

/**
 * 持锁信息。
 *
 * @author wangzhao
 */

public class LockInfo {
    private final String holder;
    private final long lockedAt;
    /**
     * Lock过期TTL
     */
    @Getter
    @Setter
    private long lockTtl;
    /**
     * Lock最大允许不被使用TTL
     */
    @Getter
    @Setter
    private long lockUnusedTtl = LOCK_UNUSED_TTL_MS;
    /**
     * 上次使用时间
     */
    @Getter
    private long lastUseTime;

    private static final long LOCK_TTL_MS = 10_000;
    private static final long LOCK_UNUSED_TTL_MS = 3600_1000;

    private LockInfo(String holder, long lockedAt, long lockTtl) {
        this.holder = holder;
        this.lockedAt = lockedAt;
        this.lockTtl = lockTtl;
        this.lastUseTime = System.currentTimeMillis();
    }

    boolean isExpired() {
        long now = System.currentTimeMillis();
        return now - lockedAt > lockTtl || now - lastUseTime > lockUnusedTtl;
    }

    public static LockInfo of(String holder) {
        return of(holder, LOCK_TTL_MS);
    }

    public static LockInfo of(String holder, long lockTtl) {
        long now = System.currentTimeMillis();
        return new LockInfo(holder, now, lockTtl);
    }

    public String getHolder() {
        this.lastUseTime = System.currentTimeMillis();
        return holder;
    }

    public long getLockedAt() {
        this.lastUseTime = System.currentTimeMillis();
        return lockedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        LockInfo lockInfo = (LockInfo) o;
        return Objects.equals(holder, lockInfo.holder);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(holder);
    }
}