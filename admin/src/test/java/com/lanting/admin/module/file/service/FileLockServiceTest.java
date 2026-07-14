package com.lanting.admin.module.file.service;

import com.lanting.admin.common.exception.BusinessException;
import com.lanting.admin.module.file.result.FileResultCode;
import com.lanting.admin.module.file.vo.AcquireLockVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FileLockService 单元测试。
 *
 * @author wangzhao
 */
@DisplayName("FileLockService 单元测试")
class FileLockServiceTest {

    private final FileLockService fileLockService = new FileLockService();

    @AfterEach
    void tearDown() {
        fileLockService.forceRelease(1L);
        fileLockService.forceRelease(2L);
    }

    @Nested
    @DisplayName("doIfHolder 语义")
    class DoIfHolder {

        @Test
        @DisplayName("持锁人执行成功并正确返回结果")
        void shouldExecuteWhenHolder() {
            fileLockService.acquire(1L, "alice");

            String result = fileLockService.doIfHolder(1L, "alice",
                    () -> "executed-by-alice");

            assertThat(result).isEqualTo("executed-by-alice");
        }

        @Test
        @DisplayName("非持锁人执行抛 FILE_LOCKED")
        void shouldThrowWhenNotHolder() {
            fileLockService.acquire(1L, "alice");

            assertThatThrownBy(() -> fileLockService.doIfHolder(1L, "bob", () -> null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getResultCode())
                    .isEqualTo(FileResultCode.FILE_LOCKED);
        }

        @Test
        @DisplayName("无人持锁时执行也应抛 FILE_LOCKED")
        void shouldThrowWhenNotLocked() {
            assertThatThrownBy(() -> fileLockService.doIfHolder(1L, "alice", () -> null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getResultCode())
                    .isEqualTo(FileResultCode.FILE_LOCKED);
        }
    }

    @Nested
    @DisplayName("acquire 基本流程")
    class Acquire {

        @Test
        @DisplayName("首次抢锁返回 previousHolder 为 null")
        void shouldAcquireFirstTime() {
            AcquireLockVO result = fileLockService.acquire(1L, "alice");

            assertThat(result.isAcquired()).isTrue();
            assertThat(result.getPreviousHolder()).isNull();
            assertThat(result.getPreviousHolderAt()).isNull();
            assertThat(fileLockService.getHolder(1L)).isEqualTo("alice");
        }

        @Test
        @DisplayName("覆盖他人锁时返回前持锁人信息")
        void shouldReturnPreviousHolderWhenOverriding() {
            fileLockService.acquire(1L, "alice");
            AcquireLockVO result = fileLockService.acquire(1L, "bob");

            assertThat(result.isAcquired()).isTrue();
            assertThat(result.getPreviousHolder()).isEqualTo("alice");
            assertThat(result.getPreviousHolderAt()).isNotNull();
            assertThat(fileLockService.getHolder(1L)).isEqualTo("bob");
        }
    }

    @Nested
    @DisplayName("release 基本流程")
    class Release {

        @Test
        @DisplayName("持锁人释放成功")
        void shouldReleaseByHolder() {
            fileLockService.acquire(1L, "alice");
            boolean released = fileLockService.release(1L, "alice");

            assertThat(released).isTrue();
            assertThat(fileLockService.getHolder(1L)).isNull();
        }

        @Test
        @DisplayName("非持锁人释放失败")
        void shouldFailReleaseByNonHolder() {
            fileLockService.acquire(1L, "alice");
            boolean released = fileLockService.release(1L, "bob");

            assertThat(released).isFalse();
            assertThat(fileLockService.getHolder(1L)).isEqualTo("alice");
        }
    }

    @Nested
    @DisplayName("forceRelease")
    class ForceRelease {

        @Test
        @DisplayName("不校验持锁人直接释放")
        void shouldReleaseRegardlessOfHolder() {
            fileLockService.acquire(1L, "alice");
            fileLockService.forceRelease(1L);

            assertThat(fileLockService.getHolder(1L)).isNull();
        }
    }

    @Nested
    @DisplayName("isHolder / getHolder / getLockedAt 查询")
    class Query {

        @Test
        @DisplayName("正常查询与未锁定情况")
        void shouldReturnCorrectLockInfo() {
            assertThat(fileLockService.getHolder(1L)).isNull();
            assertThat(fileLockService.getLockedAt(1L)).isNull();

            fileLockService.acquire(1L, "alice");

            assertThat(fileLockService.isHolder(1L, "alice")).isTrue();
            assertThat(fileLockService.isHolder(1L, "bob")).isFalse();
            assertThat(fileLockService.getHolder(1L)).isEqualTo("alice");
            assertThat(fileLockService.getLockedAt(1L)).isNotNull();
        }
    }
}
