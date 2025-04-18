package com.yang.ratingsystem.listener.thumb;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 点赞消息处理监控仪表盘
 * 提供实时统计和监控点赞消息的处理情况
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ThumbMessageDashboard {

    private final ThumbMessageRetryTracker retryTracker;
    
    private final AtomicLong totalProcessedMessages = new AtomicLong(0);
    private final AtomicLong totalSuccessMessages = new AtomicLong(0);
    private final AtomicLong totalRetryMessages = new AtomicLong(0);
    private final AtomicLong totalDeadLetterMessages = new AtomicLong(0);
    
    // 时间段内的计数器
    private final AtomicLong periodProcessedMessages = new AtomicLong(0);
    private final AtomicLong periodSuccessMessages = new AtomicLong(0);
    private final AtomicLong periodRetryMessages = new AtomicLong(0);
    private final AtomicLong periodDeadLetterMessages = new AtomicLong(0);

    /**
     * 记录消息处理
     */
    public void recordMessageProcessed() {
        totalProcessedMessages.incrementAndGet();
        periodProcessedMessages.incrementAndGet();
    }

    /**
     * 记录消息处理成功
     */
    public void recordMessageSuccess() {
        totalSuccessMessages.incrementAndGet();
        periodSuccessMessages.incrementAndGet();
    }

    /**
     * 记录消息重试
     */
    public void recordMessageRetry() {
        totalRetryMessages.incrementAndGet();
        periodRetryMessages.incrementAndGet();
    }

    /**
     * 记录消息进入死信队列
     */
    public void recordMessageDeadLetter() {
        totalDeadLetterMessages.incrementAndGet();
        periodDeadLetterMessages.incrementAndGet();
    }

    /**
     * 每分钟打印一次简要统计
     */
    @Scheduled(fixedRate = 60000)
    public void logMinuteStats() {
        long processed = periodProcessedMessages.getAndSet(0);
        long success = periodSuccessMessages.getAndSet(0);
        long retry = periodRetryMessages.getAndSet(0);
        long deadLetter = periodDeadLetterMessages.getAndSet(0);
        
        if (processed > 0) {
            log.info("[点赞消息统计-1分钟] 处理: {}, 成功: {}, 重试: {}, 死信: {}, 成功率: {}%", 
                    processed, success, retry, deadLetter, 
                    processed > 0 ? String.format("%.2f", (success * 100.0 / processed)) : "0.00");
        }
    }

    /**
     * 每小时打印一次详细统计
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void logHourlyDetailedStats() {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        
        log.info("==================================================");
        log.info("点赞消息处理统计报告 ({})", now);
        log.info("==================================================");
        log.info("总处理消息数: {}", totalProcessedMessages.get());
        log.info("总成功消息数: {}", totalSuccessMessages.get());
        log.info("总重试消息数: {}", totalRetryMessages.get());
        log.info("总死信消息数: {}", totalDeadLetterMessages.get());
        log.info("总体成功率: {}%", 
                totalProcessedMessages.get() > 0 
                ? String.format("%.2f", (totalSuccessMessages.get() * 100.0 / totalProcessedMessages.get()))
                : "0.00");
        
        // 获取当前活跃的重试消息
        int activeRetries = retryTracker.getTrackingCount();
        log.info("当前活跃重试消息: {}", activeRetries);
        
        if (activeRetries > 0) {
            log.info("活跃重试消息详情:");
            retryTracker.logActiveRetries();
        }
        
        log.info("==================================================");
    }
    
    /**
     * 提供当前点赞消息处理状态的概览
     * 可以被REST API调用以提供监控数据
     */
    public String getStatusSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("点赞消息处理状态概览:\n");
        summary.append("==================================================\n");
        summary.append(String.format("总处理消息: %d\n", totalProcessedMessages.get()));
        summary.append(String.format("总成功消息: %d\n", totalSuccessMessages.get()));
        summary.append(String.format("总重试消息: %d\n", totalRetryMessages.get()));
        summary.append(String.format("总死信消息: %d\n", totalDeadLetterMessages.get()));
        summary.append(String.format("总体成功率: %.2f%%\n", 
                totalProcessedMessages.get() > 0 
                ? (totalSuccessMessages.get() * 100.0 / totalProcessedMessages.get())
                : 0.00));
        summary.append(String.format("当前活跃重试: %d\n", retryTracker.getTrackingCount()));
        summary.append("==================================================\n");
        
        return summary.toString();
    }
} 