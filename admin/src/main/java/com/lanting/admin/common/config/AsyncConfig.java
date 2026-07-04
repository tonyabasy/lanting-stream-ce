package com.lanting.admin.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务线程池配置。
 *
 * @author wangzhao
 */
@Configuration
public class AsyncConfig {

    @Bean("lantingTaskExecutor")
    public Executor lantingTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数，当前事件量较小，设为 1 避免空闲资源浪费
        executor.setCorePoolSize(1);
        // 最大线程数，队列满时最多扩展到 5 个线程
        executor.setMaxPoolSize(5);
        // 任务队列容量
        executor.setQueueCapacity(200);
        // 线程名前缀
        executor.setThreadNamePrefix("lanting-async-");
        // 拒绝策略：由调用线程执行，保证任务不丢失
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
