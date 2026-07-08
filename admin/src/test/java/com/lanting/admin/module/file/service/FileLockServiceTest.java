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
        fileLockService.forceRelease("test/a.sql");
        fileLockService.forceRelease("test/b.sql");
    }

    @Nested
    @DisplayName("doIfHolder 语义")
    class DoIfHolder {

        @Test
        @DisplayName("持锁人执行成功并正确返回结果")
        void shouldExecuteWhenHolder() {
            fileLockService.acquire("test/a.sql", "alice");

            String result = fileLockService.doIfHolder("test/a.sql", "alice",
                    () -> "executed-by-alice");

            assertThat(result).isEqualTo("executed-by-alice");
        }

        @Test
        @DisplayName("非持锁人执行抛 FILE_LOCKED")
        void shouldThrowWhenNotHolder() {
            fileLockService.acquire("test/a.sql", "alice");

            assertThatThrownBy(() -> fileLockService.doIfHolder("test/a.sql", "bob", () -> null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getResultCode())
                    .isEqualTo(FileResultCode.FILE_LOCKED);
        }

        @Test
        @DisplayName("无人持锁时执行也应抛 FILE_LOCKED")
        void shouldThrowWhenNotLocked() {
            assertThatThrownBy(() -> fileLockService.doIfHolder("test/a.sql", "alice", () -> null))
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
            AcquireLockVO result = fileLockService.acquire("test/a.sql", "alice");

            assertThat(result.isAcquired()).isTrue();
            assertThat(result.getPreviousHolder()).isNull();
            assertThat(result.getPreviousHolderAt()).isNull();
            assertThat(fileLockService.getHolder("test/a.sql")).isEqualTo("alice");
        }

        @Test
        @DisplayName("覆盖他人锁时返回前持锁人信息")
        void shouldReturnPreviousHolderWhenOverriding() {
            fileLockService.acquire("test/a.sql", "alice");
            AcquireLockVO result = fileLockService.acquire("test/a.sql", "bob");

            assertThat(result.isAcquired()).isTrue();
            assertThat(result.getPreviousHolder()).isEqualTo("alice");
            assertThat(result.getPreviousHolderAt()).isNotNull();
            assertThat(fileLockService.getHolder("test/a.sql")).isEqualTo("bob");
        }
    }

    @Nested
    @DisplayName("release 基本流程")
    class Release {

        @Test
        @DisplayName("持锁人释放成功")
        void shouldReleaseByHolder() {
            fileLockService.acquire("test/a.sql", "alice");
            boolean released = fileLockService.release("test/a.sql", "alice");

            assertThat(released).isTrue();
            assertThat(fileLockService.getHolder("test/a.sql")).isNull();
        }

        @Test
        @DisplayName("非持锁人释放失败")
        void shouldFailReleaseByNonHolder() {
            fileLockService.acquire("test/a.sql", "alice");
            boolean released = fileLockService.release("test/a.sql", "bob");

            assertThat(released).isFalse();
            assertThat(fileLockService.getHolder("test/a.sql")).isEqualTo("alice");
        }
    }

    @Nested
    @DisplayName("forceRelease")
    class ForceRelease {

        @Test
        @DisplayName("不校验持锁人直接释放")
        void shouldReleaseRegardlessOfHolder() {
            fileLockService.acquire("test/a.sql", "alice");
            fileLockService.forceRelease("test/a.sql");

            assertThat(fileLockService.getHolder("test/a.sql")).isNull();
        }
    }

    @Nested
    @DisplayName("isHolder / getHolder / getLockedAt 查询")
    class Query {

        @Test
        @DisplayName("正常查询与未锁定情况")
        void shouldReturnCorrectLockInfo() {
            assertThat(fileLockService.getHolder("test/a.sql")).isNull();
            assertThat(fileLockService.getLockedAt("test/a.sql")).isNull();

            fileLockService.acquire("test/a.sql", "alice");

            assertThat(fileLockService.isHolder("test/a.sql", "alice")).isTrue();
            assertThat(fileLockService.isHolder("test/a.sql", "bob")).isFalse();
            assertThat(fileLockService.getHolder("test/a.sql")).isEqualTo("alice");
            assertThat(fileLockService.getLockedAt("test/a.sql")).isNotNull();
        }
    }
}
