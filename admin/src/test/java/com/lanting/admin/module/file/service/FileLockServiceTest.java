package com.lanting.admin.module.file.service;

import com.lanting.admin.common.exception.BusinessException;
import com.lanting.admin.module.file.entity.FileIndexEntity;
import com.lanting.admin.module.file.result.FileResultCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * FileLockService 单元测试 — 目录锁功能。
 *
 * @author wangzhao
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FileLockService 目录锁测试")
class FileLockServiceTest {

    @Mock
    private FileIndexService fileIndexService;

    private FileLockService fileLockService;

    @BeforeEach
    void setUp() {
        fileLockService = new FileLockService(fileIndexService);
    }

    @AfterEach
    void tearDown() {
        fileLockService.releaseFolderLock("sql", "bob");
        fileLockService.releaseFolderLock("sql", "user1");
        fileLockService.releaseFolderLock("sql", "user2");
        fileLockService.releaseFolderLock("sql/etl", "user1");
        fileLockService.forceRelease(1L);
        fileLockService.forceRelease(2L);
    }

    private FileIndexEntity fileEntity(Long id, String path, String type) {
        FileIndexEntity e = new FileIndexEntity();
        e.setId(id);
        e.setPath(path);
        e.setType(type);
        return e;
    }

    @Nested
    @DisplayName("目录锁基本行为")
    class FolderLockBasics {

        @Test
        @DisplayName("远端祖先被他人锁定时，文件操作应被拦截")
        void shouldBlockFileOpWhenAncestorLockedByOther() {
            // bob 锁定目录 sql
            when(fileIndexService.listAllChildren("sql")).thenReturn(Collections.emptyList());
            fileLockService.acquireFolderLock("sql", "bob");

            // alice 尝试获取 sql/etl/daily/init.sql 的文件锁，应被 ensureFolderLocksSafety 拦截
            assertThatThrownBy(() -> fileLockService.acquire(1L, "sql/etl/daily/init.sql", "alice"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getResultCode())
                    .isEqualTo(FileResultCode.FILE_LOCKED);
        }

        @Test
        @DisplayName("TTL 过期后目录锁自动清理，文件操作恢复")
        void shouldCleanExpiredLockOnCheck() {
            // bob 锁定目录 sql
            when(fileIndexService.listAllChildren("sql")).thenReturn(Collections.emptyList());
            fileLockService.acquireFolderLock("sql", "bob");

            // 将锁的 lockedAt 设为 11 秒前，模拟过期
            expireFolderLock("sql");

            // alice 操作子文件，过期锁应被惰性清理，不抛异常
            assertThatCode(() -> fileLockService.acquire(1L, "sql/etl/init.sql", "alice"))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("并发场景：目录删除 vs 文件写入")
    class ConcurrentWriteAndDelete {

        @Test
        @DisplayName("user2 正在写入子文件时，user1 删除目录应封门后等待写入完成再接管")
        void shouldBlockOnWriteThenTakeOver() throws Exception {
            // 目录 sql 下有一个文件 f1.txt(id=1)
            when(fileIndexService.listAllChildren("sql"))
                    .thenReturn(List.of(fileEntity(1L, "sql/f1.txt", "file")));

            // user2 先抢到 f1.txt 的锁
            fileLockService.acquire(1L, "sql/f1.txt", "user2");

            CountDownLatch insideWrite = new CountDownLatch(1);
            CountDownLatch canFinish = new CountDownLatch(1);

            ExecutorService executor = Executors.newFixedThreadPool(2);

            // user2 开始写入（进入 doIfHolder 的 synchronized 块内）
            Future<String> writeResult = executor.submit(() ->
                    fileLockService.doIfHolder(1L, "sql/f1.txt", "user2", () -> {
                        insideWrite.countDown();          // 信号：已进入 stripe
                        try {
                            canFinish.await();             // 等待释放信号
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return "write-done";
                    })
            );

            // 等待 user2 进入 stripe
            insideWrite.await(2, TimeUnit.SECONDS);

            // user1 删除目录
            Future<?> deleteResult = executor.submit(() -> {
                fileLockService.acquireFolderLock("sql", "user1");
            });

            // user1 的 acquire(1L, ...) 应阻塞在 stripe 上等待 user2 写完
            Thread.sleep(200);
            assertThat(writeResult.isDone()).isFalse();
            assertThat(deleteResult.isDone()).isFalse();

            // 释放 user2
            canFinish.countDown();
            assertThat(writeResult.get(2, TimeUnit.SECONDS)).isEqualTo("write-done");

            // user1 现在应能完成删除（获得目录锁和文件锁）
            deleteResult.get(2, TimeUnit.SECONDS);

            // 验证：user1 接管了 f1.txt
            assertThat(fileLockService.getHolder(1L)).isEqualTo("user1");

            executor.shutdown();
        }
    }

    @Nested
    @DisplayName("并发场景：移动子目录 vs 删父目录")
    class MoveSubFolderAndDeleteParent {

        @Test
        @DisplayName("user1 移动 sql/etl 时，user2 删除 sql 应被拦截")
        void shouldBlockDeleteWhenSubFolderLocked() {
            // user1 锁定子目录 sql/etl（A/B）
            when(fileIndexService.listAllChildren("sql/etl")).thenReturn(Collections.emptyList());
            fileLockService.acquireFolderLock("sql/etl", "user1");

            // user2 尝试删除父目录 sql（A），ensureFolderLocksSafety 应检测到子目录锁并直接抛异常
            assertThatThrownBy(() -> fileLockService.acquireFolderLock("sql", "user2"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getResultCode())
                    .isEqualTo(FileResultCode.FILE_LOCKED);
        }
    }

    /**
     * 测试辅助：将指定路径的目录锁 lockedAt 设为 11 秒前，模拟过期。
     */
    private void expireFolderLock(String path) {
        try {
            var field = FileLockService.class.getDeclaredField("folderHardLocks");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            var map = (java.util.Map<String, ?>) field.get(fileLockService);

            // LockInfo 是 record，无法直接修改字段。通过反射构造新实例 put 回去。
            Class<?> lockInfoClass = FileLockService.class.getDeclaredClasses()[0];
            var constructor = lockInfoClass.getDeclaredConstructor(String.class, long.class);
            constructor.setAccessible(true);
            Object oldLock = map.get(path);
            if (oldLock != null) {
                String holder = (String) lockInfoClass.getMethod("holder").invoke(oldLock);
                Object expiredLock = constructor.newInstance(holder, System.currentTimeMillis() - 11_000);
                @SuppressWarnings("unchecked")
                var rawMap = (java.util.Map<String, Object>) map;
                rawMap.put(path, expiredLock);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
