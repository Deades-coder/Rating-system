package com.yang.ratingsystem.listener.thumb;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.MessageId;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 消息重试跟踪器
 * 用于记录和监控消息重试情况
 */
@Component
@Slf4j
public class ThumbMessageRetryTracker {

    /**
     * 消息重试记录
     */
    private final Map<String, RetryRecord> retryRecords = new ConcurrentHashMap<>();
    
    /**
     * 清理线程，定期清理过期的重试记录
     */
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor();
    
    public ThumbMessageRetryTracker() {
        // 每5分钟执行一次清理
        cleanupExecutor.scheduleAtFixedRate(this::cleanup, 5, 5, TimeUnit.MINUTES);
    }
    
    /**
     * 记录消息开始处理
     */
    public void trackMessageProcessing(MessageId messageId, String messageInfo) {
        String key = messageId.toString();
        RetryRecord record = retryRecords.computeIfAbsent(key, k -> new RetryRecord());
        record.setMessageId(key);
        record.setMessageInfo(messageInfo);
        record.setFirstProcessTime(LocalDateTime.now());
        record.setLastProcessTime(record.getFirstProcessTime());
        record.setRetryCount(0);
        log.debug("开始跟踪消息处理: {}", record);
    }
    
    /**
     * 记录消息处理成功
     */
    public void trackMessageSuccess(MessageId messageId) {
        String key = messageId.toString();
        RetryRecord record = retryRecords.get(key);
        if (record != null) {
            record.setSuccessTime(LocalDateTime.now());
            record.setStatus("成功");
            if (record.getRetryCount() > 0) {
                log.info("消息成功处理，经过{}次重试: {}", record.getRetryCount(), record);
            }
            // 成功的记录可以较快清理
            cleanupExecutor.schedule(() -> retryRecords.remove(key), 1, TimeUnit.MINUTES);
        }
    }
    
    /**
     * 记录消息处理失败，准备重试
     */
    public void trackMessageRetry(MessageId messageId, Exception exception) {
        String key = messageId.toString();
        RetryRecord record = retryRecords.get(key);
        if (record != null) {
            record.setRetryCount(record.getRetryCount() + 1);
            record.setLastProcessTime(LocalDateTime.now());
            record.setLastError(exception.getMessage());
            record.setStatus("重试中");
            log.warn("消息处理失败，准备第{}次重试: {}, 错误: {}", 
                    record.getRetryCount(), record, exception.getMessage());
        }
    }
    
    /**
     * 记录消息处理达到最大重试次数，将进入死信队列
     */
    public void trackMessageDeadLetter(MessageId messageId) {
        String key = messageId.toString();
        RetryRecord record = retryRecords.get(key);
        if (record != null) {
            record.setDeadLetterTime(LocalDateTime.now());
            record.setStatus("死信");
            log.error("消息处理失败，达到最大重试次数，进入死信队列: {}", record);
        }
    }
    
    /**
     * 清理过期的重试记录
     */
    private void cleanup() {
        LocalDateTime expirationTime = LocalDateTime.now().minusHours(24);
        int count = 0;
        for (Map.Entry<String, RetryRecord> entry : retryRecords.entrySet()) {
            RetryRecord record = entry.getValue();
            if (record.getLastProcessTime().isBefore(expirationTime)) {
                retryRecords.remove(entry.getKey());
                count++;
            }
        }
        if (count > 0) {
            log.info("清理了{}条过期的重试记录", count);
        }
    }
    
    /**
     * 获取当前跟踪的消息数量
     */
    public int getTrackingCount() {
        return retryRecords.size();
    }
    
    /**
     * 打印当前所有活跃的重试记录
     */
    public void logActiveRetries() {
        retryRecords.forEach((key, record) -> {
            if ("重试中".equals(record.getStatus())) {
                log.info("活跃重试消息: [messageId={}] [info={}] [重试次数={}] [最后错误={}] [最后处理时间={}]",
                        record.getMessageId(),
                        record.getMessageInfo(),
                        record.getRetryCount(),
                        record.getLastError(),
                        record.getLastProcessTime());
            }
        });
    }
    
    /**
     * 消息重试记录数据结构
     */
    @Data
    public static class RetryRecord {
        private String messageId;
        private String messageInfo;
        private LocalDateTime firstProcessTime;
        private LocalDateTime lastProcessTime;
        private LocalDateTime successTime;
        private LocalDateTime deadLetterTime;
        private int retryCount;
        private String lastError;
        private String status = "处理中";
    }
} 