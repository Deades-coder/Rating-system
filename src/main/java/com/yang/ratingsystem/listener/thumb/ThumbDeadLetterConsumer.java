package com.yang.ratingsystem.listener.thumb;

import com.yang.ratingsystem.listener.thumb.msg.ThumbEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 点赞死信队列消费者
 * 用于处理经过多次重试后仍然失败的点赞消息
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ThumbDeadLetterConsumer {

    private final PulsarClient pulsarClient;
    
    @Value("${pulsar.topic:thumb-topic}")
    private String thumbTopic;
    
    private Consumer<ThumbEvent> deadLetterConsumer;
    
    @PostConstruct
    public void init() {
        try {
            // 死信队列主题名称
            String deadLetterTopic = "thumb-dlq-topic";
            
            // 创建死信队列消费者
            deadLetterConsumer = pulsarClient.newConsumer(Schema.JSON(ThumbEvent.class))
                    .topic(deadLetterTopic)
                    .subscriptionName("thumb-dlq-subscription")
                    .subscriptionType(SubscriptionType.Shared)
                    .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
                    .negativeAckRedeliveryDelay(60, TimeUnit.SECONDS)
                    .acknowledgmentGroupTime(0, TimeUnit.MILLISECONDS)
                    .subscribe();
            
            // 启动消息处理线程
            new Thread(this::processDeadLetters).start();
            log.info("点赞死信队列消费者初始化成功，订阅主题: {}", deadLetterTopic);
        } catch (PulsarClientException e) {
            log.error("初始化点赞死信队列消费者失败", e);
        }
    }
    
    private void processDeadLetters() {
        while (!Thread.currentThread().isInterrupted() && deadLetterConsumer != null) {
            try {
                // 单条接收消息
                Message<ThumbEvent> message = deadLetterConsumer.receive(5, TimeUnit.SECONDS);
                if (message != null) {
                    ThumbEvent event = message.getValue();
                    if (event != null) {
                        try {
                            // 记录死信消息，可以进行告警或持久化到特定的表中
                            logDeadLetter(event, message);
                            
                            // 确认消息
                            deadLetterConsumer.acknowledge(message);
                            log.info("死信消息处理成功: messageId={}, event={}", message.getMessageId(), event);
                        } catch (Exception e) {
                            log.error("处理死信消息失败: messageId={}, event={}", message.getMessageId(), event, e);
                            // 重新排队
                            deadLetterConsumer.negativeAcknowledge(message);
                        }
                    } else {
                        // 无效消息直接确认
                        deadLetterConsumer.acknowledge(message);
                    }
                }
            } catch (PulsarClientException e) {
                if (e instanceof PulsarClientException.AlreadyClosedException) {
                    // 消费者已关闭，退出循环
                    break;
                }
                log.error("接收死信消息异常", e);
                try {
                    // 等待一段时间后重试
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
    
    /**
     * 记录死信消息
     * 可以扩展为写入数据库或发送告警
     */
    private void logDeadLetter(ThumbEvent event, Message<ThumbEvent> message) {
        log.warn("收到死信消息: messageId={}, userId={}, blogId={}, type={}, eventTime={}, properties={}",
                message.getMessageId(),
                event.getUserId(),
                event.getBlogId(),
                event.getType(),
                event.getEventTime(),
                message.getProperties());
        
        // TODO: 可以在此处添加业务处理逻辑，例如：
        // 1. 插入到死信记录表
        // 2. 发送告警通知
        // 3. 尝试特殊的恢复逻辑
    }
    
    @PreDestroy
    public void destroy() {
        try {
            if (deadLetterConsumer != null) {
                deadLetterConsumer.close();
            }
        } catch (PulsarClientException e) {
            log.error("关闭死信队列消费者失败", e);
        }
    }
} 