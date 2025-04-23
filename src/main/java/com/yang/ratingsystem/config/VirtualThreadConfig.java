package com.yang.ratingsystem.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
/**
 * User:小小星仔
 * Date:2025-04-18
 * Time:15:31
 */
/**
 * 虚拟线程配置类
 * 使用JDK 21的虚拟线程特性提高并发处理能力
 * 
 * 虚拟线程是轻量级线程，由JVM而不是操作系统管理，可以创建数百万个实例
 * 适用于I/O密集型任务，如网络请求、数据库操作等
 */
@Configuration
@EnableAsync
public class VirtualThreadConfig implements AsyncConfigurer {
    
    /**
     * 配置默认的异步任务执行器为虚拟线程执行器
     * 所有使用@Async注解的方法将默认使用此执行器
     * 无需大量线程池配置，可自动扩展到数百万并发任务
     */
    @Override
    @Bean
    public Executor getAsyncExecutor() {
        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
    }
    
    /**
     * 创建虚拟线程执行服务
     * 用于需要手动提交任务的场景
     * 例如：需要获取返回值的任务，使用submit()方法提交
     */
    @Bean
    public ExecutorService virtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
    
    /**
     * 创建命名的虚拟线程工厂
     * 用于创建带有特定名称前缀的虚拟线程
     * 便于在日志和线程转储中识别线程来源
     */
    @Bean
    public ThreadFactory namedVirtualThreadFactory() {
        return Thread.ofVirtual().name("vt-", 0).factory();
    }
    
    /**
     * 为Spring框架内部使用配置虚拟线程执行器
     * 将Spring内部使用的线程池任务转换为虚拟线程执行
     * 提升整个应用的并发处理能力
     */
    @Bean
    public ThreadPoolTaskExecutor applicationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 使用虚拟线程作为工作线程，每个任务都在新的虚拟线程中执行
        executor.setTaskDecorator(runnable -> () -> {
            try {
                Thread.ofVirtual().name("spring-vt-").start(runnable).join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        });
        // 核心池大小设为0，所有任务都会创建新的虚拟线程
        executor.setCorePoolSize(0);
        // 最大线程数设置为足够大的值（虚拟线程非常轻量）
        executor.setMaxPoolSize(Integer.MAX_VALUE);
        executor.setThreadNamePrefix("app-virtual-");
        executor.initialize();
        return executor;
    }
} 