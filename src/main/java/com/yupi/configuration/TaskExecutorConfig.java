package com.yupi.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步线程池配置。
 * <p>
 * 原来这个 {@code @Bean} 定义在 {@code PictureServiceImpl}（一个 @Service）内部，
 * 同时该类又通过 {@code @Resource(name = "taskExecutor")} 注入自己声明的 Bean，
 * 形成了自引用；而 {@code PictureServiceImpl} 因为 {@code @Async} 会被代理，
 * 导致 Spring 报 "injected in its raw version ... but has eventually been wrapped"。
 * 这里把它移到配置类中，消除自引用。
 */
@Configuration
public class TaskExecutorConfig {

    @Bean("taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数
        executor.setCorePoolSize(10);
        // 最大线程数
        executor.setMaxPoolSize(20);
        // 队列容量
        executor.setQueueCapacity(200);
        // 线程名前缀
        executor.setThreadNamePrefix("async-service-");
        // 拒绝策略：由调用线程处理该任务
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 等待所有任务结束后再关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}
