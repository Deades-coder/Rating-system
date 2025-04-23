package com.yang.ratingsystem.listener.thumb;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yang.ratingsystem.filter.BloomFilterService;
import com.yang.ratingsystem.listener.thumb.msg.ThumbEvent;
import com.yang.ratingsystem.mapper.BlogMapper;
import com.yang.ratingsystem.model.Thumb;
import com.yang.ratingsystem.service.ThumbService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.*;
import org.apache.pulsar.client.impl.MultiplierRedeliveryBackoff;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Lazy;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import cn.hutool.core.lang.Pair;

/**
 * 点赞消息消费者
 * 
 * 负责从Pulsar消息队列中消费点赞事件消息，并异步处理
 * 使用虚拟线程技术提高消息处理的并发能力和资源利用率
 * 实现了消息的批量处理、重试机制和死信队列处理
 */
@Service
@Slf4j
public class ThumbConsumer {

    private final BlogMapper blogMapper;
    private final ThumbService thumbService;
    private final PulsarClient pulsarClient;
    private final ThumbMessageRetryTracker retryTracker; // 消息重试跟踪器
    private final ThumbMessageDashboard dashboard;       // 消息处理仪表盘
    private final BloomFilterService bloomFilterService; // 布隆过滤器服务
    
    @Value("${pulsar.topic:thumb-topic}")
    private String thumbTopic;
    
    @Value("${pulsar.max-retry-attempts:3}")
    private int maxRetryAttempts;
    
    private Consumer<ThumbEvent> consumer;
    
    public ThumbConsumer(
            BlogMapper blogMapper,
            ThumbService thumbService,
            PulsarClient pulsarClient,
            ThumbMessageRetryTracker retryTracker,
            ThumbMessageDashboard dashboard,
            @Lazy BloomFilterService bloomFilterService) {
        this.blogMapper = blogMapper;
        this.thumbService = thumbService;
        this.pulsarClient = pulsarClient;
        this.retryTracker = retryTracker;
        this.dashboard = dashboard;
        this.bloomFilterService = bloomFilterService;
    }
    
    /**
     * 初始化消费者
     * 配置Pulsar消费者参数并启动消息处理线程
     */
    @PostConstruct
    public void init() {
        try {
            // 创建消费者，配置消息处理参数
            consumer = pulsarClient.newConsumer(Schema.JSON(ThumbEvent.class))
                    .topic(thumbTopic)
                    .subscriptionName("thumb-subscription")
                    .subscriptionType(SubscriptionType.Shared)         // 共享订阅，支持多消费者负载均衡
                    .subscriptionInitialPosition(SubscriptionInitialPosition.Latest) // 从最新消息开始消费
                    .negativeAckRedeliveryDelay(2, TimeUnit.SECONDS)   // 否定确认后消息重新投递延迟
                    .acknowledgmentGroupTime(0, TimeUnit.MILLISECONDS) // 立即确认，不分组
                    .deadLetterPolicy(DeadLetterPolicy.builder()       // 配置死信策略
                            .maxRedeliverCount(maxRetryAttempts)
                            .deadLetterTopic("thumb-dlq-topic")
                            .build())
                    .negativeAckRedeliveryBackoff(MultiplierRedeliveryBackoff.builder() // 指数退避重试策略
                            .minDelayMs(1000)
                            .maxDelayMs(60000)
                            .multiplier(2)
                            .build())
                    .ackTimeoutRedeliveryBackoff(MultiplierRedeliveryBackoff.builder()
                            .minDelayMs(5000)
                            .maxDelayMs(300_000)
                            .multiplier(3)
                            .build())
                    .receiverQueueSize(1000)
                    .subscribe();
            
            // 启动消息处理线程
            new Thread(this::receiveMessages).start();
            log.info("点赞消息消费者初始化成功，订阅主题: {}", thumbTopic);
        } catch (PulsarClientException e) {
            log.error("初始化点赞消息消费者失败", e);
        }
    }
    
    /**
     * 持续接收并处理消息的主循环
     * 使用批量接收提高处理效率
     */
    private void receiveMessages() {
        while (!Thread.currentThread().isInterrupted() && consumer != null) {
            try {
                // 批量接收消息
                Messages<ThumbEvent> messages = consumer.batchReceive();
                if (messages != null) {
                    List<MessageId> messageIds = new ArrayList<>();
                    List<ThumbEvent> events = new ArrayList<>();
                    Map<MessageId, Message<ThumbEvent>> messageMap = new HashMap<>();
                    
                    // 收集消息并准备处理
                    messages.forEach(msg -> {
                        ThumbEvent event = msg.getValue();
                        if (event != null) {
                            // 获取重试次数
                            Integer redeliveryCount = getRedeliveryCount(msg);
                            
                            // 跟踪消息处理状态
                            retryTracker.trackMessageProcessing(
                                    msg.getMessageId(),
                                    String.format("userId=%s, blogId=%s, type=%s, retryCount=%d",
                                            event.getUserId(), event.getBlogId(), event.getType(), redeliveryCount)
                            );
                            
                            // 记录消息处理
                            dashboard.recordMessageProcessed();
                            
                            events.add(event);
                            messageIds.add(msg.getMessageId());
                            messageMap.put(msg.getMessageId(), msg);
                        }
                    });
                    
                    if (!events.isEmpty()) {
                        // 创建虚拟线程执行器处理消息批次
                        // 相比传统线程池，虚拟线程资源占用极小，适合大量并发IO操作
                        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
                        try {
                            // 在虚拟线程中异步处理消息批次，但同步等待结果
                            Future<Boolean> future = executor.submit(() -> {
                                try {
                                    // 处理消息批次，包含数据库操作
                                    processBatch(events);
                                    return true;
                                } catch (Exception e) {
                                    log.error("虚拟线程处理消息批次失败", e);
                                    return false;
                                }
                            });

                            // 获取处理结果，设置超时避免无限等待
                            boolean success = future.get(30, TimeUnit.SECONDS);
                            
                            if (success) {
                                // 处理成功，确认所有消息
                                consumer.acknowledge(messageIds);
                                
                                // 记录处理成功
                                for (MessageId messageId : messageIds) {
                                    retryTracker.trackMessageSuccess(messageId);
                                    dashboard.recordMessageSuccess();
                                }
                                
                                log.info("成功处理并确认{}条消息", messageIds.size());
                            } else {
                                throw new Exception("消息处理失败");
                            }
                        } catch (Exception e) {
                            log.error("处理消息批次失败，进行否定确认，稍后重试", e);
                            
                            // 处理失败时，进行否定确认，Pulsar会在稍后重新投递消息
                            for (MessageId messageId : messageIds) {
                                Message<ThumbEvent> msg = messageMap.get(messageId);
                                Integer redeliveryCount = getRedeliveryCount(msg);
                                
                                // 记录重试信息
                                retryTracker.trackMessageRetry(messageId, e);
                                dashboard.recordMessageRetry();
                                
                                // 如果即将达到最大重试次数，记录即将进入死信队列
                                if (redeliveryCount >= maxRetryAttempts - 1) {
                                    retryTracker.trackMessageDeadLetter(messageId);
                                    dashboard.recordMessageDeadLetter();
                                    log.warn("消息即将进入死信队列: messageId={}, redeliveryCount={}", 
                                            messageId, redeliveryCount);
                                }
                                
                                consumer.negativeAcknowledge(messageId);
                            }
                        } finally {
                            // 关闭执行器，确保资源释放
                            executor.shutdown();
                            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                                log.warn("执行器未能在超时时间内完全关闭");
                                executor.shutdownNow();
                            }
                        }
                    }
                }
            } catch (PulsarClientException e) {
                log.error("接收消息异常", e);
                try {
                    // 等待一段时间后重试
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } catch (Exception e) {
                log.error("处理消息过程中发生未预期的异常", e);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
    
    /**
     * 获取消息重试次数
     */
    private Integer getRedeliveryCount(Message<?> msg) {
        String countStr = msg.getProperty("RECONSUMETIMES");
        if (countStr != null) {
            try {
                return Integer.parseInt(countStr);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        // 检查其他可能的属性名
        String redeliveryCount = msg.getProperty("redeliveryCount");
        if (redeliveryCount != null) {
            try {
                return Integer.parseInt(redeliveryCount);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }
    
    @PreDestroy
    public void destroy() {
        try {
            if (consumer != null) {
                consumer.close();
            }
        } catch (PulsarClientException e) {
            log.error("关闭消费者失败", e);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void processBatch(List<ThumbEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        
        log.info("处理点赞消息批次: 数量={}", events.size());
        Map<Long, Long> countMap = new ConcurrentHashMap<>();
        List<Thumb> thumbsToInsert = new ArrayList<>();
        LambdaQueryWrapper<Thumb> wrapper = new LambdaQueryWrapper<>();
        AtomicReference<Boolean> needRemove = new AtomicReference<>(false);

        // 按(userId, blogId)分组，并获取每个分组的最新事件
        Map<Pair<Long, Long>, ThumbEvent> latestEvents = events.stream()
                .collect(Collectors.groupingBy(
                        e -> Pair.of(e.getUserId(), e.getBlogId()),
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                list -> {
                                    // 按时间升序排序，取最后一个作为最新事件
                                    list.sort(Comparator.comparing(ThumbEvent::getEventTime));
                                    if (list.size() % 2 == 0) {
                                        return null;
                                    }
                                    return list.get(list.size() - 1);
                                }
                        )
                ));

        // 收集所有要增加的点赞(userId, blogId)对
        Set<Pair<Long, Long>> potentialInserts = new HashSet<>();
        latestEvents.forEach((userBlogPair, event) -> {
            if (event == null) {
                return;
            }
            if (event.getType() == ThumbEvent.EventType.INCR) {
                potentialInserts.add(userBlogPair);
            }
        });
        
        // 如果有要增加的点赞，先查询数据库检查哪些记录已存在
        Set<Pair<Long, Long>> existingRecords = new HashSet<>();
        if (!potentialInserts.isEmpty()) {
            LambdaQueryWrapper<Thumb> existsQuery = new LambdaQueryWrapper<>();
            for (Pair<Long, Long> pair : potentialInserts) {
                existsQuery.or(q -> q.eq(Thumb::getUserId, pair.getKey()).eq(Thumb::getBlogId, pair.getValue()));
            }
            
            List<Thumb> existingThumbs = thumbService.list(existsQuery);
            for (Thumb thumb : existingThumbs) {
                existingRecords.add(Pair.of(thumb.getUserId(), thumb.getBlogId()));
            }
            
            log.info("已存在的点赞记录数: {}", existingRecords.size());
        }

        latestEvents.forEach((userBlogPair, event) -> {
            if (event == null) {
                return;
            }
            ThumbEvent.EventType finalAction = event.getType();

            if (finalAction == ThumbEvent.EventType.INCR) {
                // 只有不存在的记录才添加到待插入列表
                if (!existingRecords.contains(userBlogPair)) {
                    countMap.merge(event.getBlogId(), 1L, Long::sum);
                    Thumb thumb = new Thumb();
                    thumb.setBlogId(event.getBlogId());
                    thumb.setUserId(event.getUserId());
                    thumbsToInsert.add(thumb);
                } else {
                    log.info("跳过已存在的点赞记录: userId={}, blogId={}", 
                            userBlogPair.getKey(), userBlogPair.getValue());
                }
            } else {
                needRemove.set(true);
                wrapper.or().eq(Thumb::getUserId, event.getUserId()).eq(Thumb::getBlogId, event.getBlogId());
                countMap.merge(event.getBlogId(), -1L, Long::sum);
            }
        });

        // 批量更新数据库
        if (needRemove.get() && wrapper.getExpression() != null && !wrapper.getExpression().getNormal().isEmpty()) {
            thumbService.remove(wrapper);
        }
        
        // 更新博客点赞数
        if (!countMap.isEmpty()) {
            batchUpdateBlogs(countMap);
        }
        
        // 批量插入点赞记录
        if (!thumbsToInsert.isEmpty()) {
            batchInsertThumbs(thumbsToInsert);
        }
        
        log.info("批量处理完成: 更新博客{}个, 插入点赞{}条", countMap.size(), thumbsToInsert.size());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void batchUpdateBlogs(Map<Long, Long> countMap) {
        try {
            blogMapper.batchUpdateThumbCount(countMap);
            log.info("批量更新博客点赞数成功: {}", countMap.keySet());
        } catch (Exception e) {
            log.error("批量更新博客点赞数失败", e);
            throw e;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void batchInsertThumbs(List<Thumb> thumbs) {
        if (thumbs.isEmpty()) {
            return;
        }
        
        try {
            // 使用忽略重复记录的方式批量插入
            try {
                // 分批次插入
                thumbService.saveBatch(thumbs, 500);
                log.info("批量插入点赞记录成功: {} 条", thumbs.size());
                
                // 将插入成功的记录添加到布隆过滤器
                for (Thumb thumb : thumbs) {
                    bloomFilterService.add(thumb.getUserId(), thumb.getBlogId());
                }
                log.info("布隆过滤器已更新: 添加 {} 条记录", thumbs.size());
            } catch (Exception e) {
                // 如果批量插入失败，尝试逐条插入，跳过重复记录
                if (e instanceof org.springframework.dao.DuplicateKeyException) {
                    log.warn("批量插入时发生唯一键冲突，尝试逐条插入并跳过重复记录");
                    int successCount = 0;
                    for (Thumb thumb : thumbs) {
                        try {
                            // 检查记录是否已存在
                            boolean exists = thumbService.lambdaQuery()
                                    .eq(Thumb::getUserId, thumb.getUserId())
                                    .eq(Thumb::getBlogId, thumb.getBlogId())
                                    .exists();
                            
                            if (!exists) {
                                thumbService.save(thumb);
                                // 将成功插入的记录添加到布隆过滤器
                                bloomFilterService.add(thumb.getUserId(), thumb.getBlogId());
                                successCount++;
                            } else {
                                // 已存在的记录也要确保在布隆过滤器中
                                bloomFilterService.add(thumb.getUserId(), thumb.getBlogId());
                            }
                        } catch (Exception innerEx) {
                            log.warn("插入单条点赞记录失败: userId={}, blogId={}, 错误: {}", 
                                    thumb.getUserId(), thumb.getBlogId(), innerEx.getMessage());
                        }
                    }
                    log.info("逐条插入完成，成功插入: {} 条，更新布隆过滤器", successCount);
                } else {
                    throw e; // 重新抛出非重复键的异常
                }
            }
        } catch (Exception e) {
            log.error("批量插入点赞记录失败", e);
            throw e;
        }
    }
}

