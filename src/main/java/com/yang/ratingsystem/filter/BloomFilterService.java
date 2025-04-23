package com.yang.ratingsystem.filter;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.yang.ratingsystem.model.Thumb;
import com.yang.ratingsystem.service.ThumbService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 布隆过滤器服务
 * 
 * 使用布隆过滤器快速判断用户是否对博客点赞，大幅提升查询性能。
 * 布隆过滤器的特性：
 * 1. 查询速度极快，时间复杂度O(1)
 * 2. 空间效率高，每个元素只占几个比特
 * 3. 可能存在误判（将不存在误判为存在），但不会漏报（将存在误判为不存在）
 * 4. 不支持删除元素，需要定期重建
 * 
 * 本实现结合虚拟线程实现高效的数据加载和定期重建，
 * 支持每日自动更新和应用启动时初始化，保证数据一致性。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class BloomFilterService {

    // 使用ApplicationContext代替直接注入ThumbService，避免循环依赖
    private final ApplicationContext applicationContext;
    
    // 布隆过滤器预计元素数量
    private static final int EXPECTED_INSERTIONS = 1_000_000;
    
    // 布隆过滤器误判率
    private static final double FPP = 0.001;
    
    // 使用读写锁保护布隆过滤器的并发访问
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    
    // 布隆过滤器实例
    private BloomFilter<String> thumbBloomFilter;
    
    /**
     * 初始化布隆过滤器并加载数据
     */
    @PostConstruct
    public void init() {
        // 先创建空的布隆过滤器
        thumbBloomFilter = BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8),
                EXPECTED_INSERTIONS,
                FPP
        );
        
        // 延迟加载数据，避免启动时的循环依赖
        // recreateBloomFilter方法会在应用完全启动后通过定时任务执行
    }
    
    /**
     * 获取ThumbService的实例
     * 使用懒加载模式避免循环依赖
     */
    private ThumbService getThumbService() {
        // 指定具体的ThumbService实现，这里使用"thumbService"作为默认实现
        return applicationContext.getBean("thumbService", ThumbService.class);
    }
    
    /**
     * 重新创建布隆过滤器并加载所有点赞数据
     */
    public void recreateBloomFilter() {
        // 创建新的布隆过滤器
        BloomFilter<String> newFilter = BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8),
                EXPECTED_INSERTIONS,
                FPP
        );
        
        log.info("开始重建点赞布隆过滤器...");
        
        try {
            // 获取ThumbService
            ThumbService thumbService = getThumbService();
            long total = thumbService.count();
            
            if (total == 0) {
                log.info("没有点赞数据，创建空的布隆过滤器");
                lock.writeLock().lock();
                try {
                    this.thumbBloomFilter = newFilter;
                } finally {
                    lock.writeLock().unlock();
                }
                return;
            }
            
            // 计算分片策略
            int batchSize = 10000;
            int numBatches = (int) Math.ceil((double) total / batchSize);
            log.info("点赞数据总量: {}, 将分为 {} 批处理，每批 {} 条记录", total, numBatches, batchSize);
            
            // 创建计数器
            var processedCounter = new java.util.concurrent.atomic.AtomicLong(0);
            
            // 使用虚拟线程处理每个分片，每个批次使用独立的虚拟线程
            // 高效利用系统资源，实现真正的并行处理
            List<Thread> workers = new ArrayList<>();
            for (int batch = 0; batch < numBatches; batch++) {
                final int currentBatch = batch;
                // 创建虚拟线程处理每个批次数据，提高数据加载速度
                Thread worker = Thread.ofVirtual()
                        .name("bloom-loader-" + currentBatch)
                        .start(() -> {
                            try {
                                int offset = currentBatch * batchSize;
                                // 分页查询点赞记录，避免一次加载过多数据
                                List<Thumb> thumbs = thumbService.lambdaQuery()
                                        .last("LIMIT " + offset + "," + batchSize)
                                        .list();
                                
                                // 将记录添加到布隆过滤器（使用局部同步，只锁定添加操作）
                                // 使用细粒度锁，减少线程竞争
                                for (Thumb thumb : thumbs) {
                                    String key = buildKey(thumb.getUserId(), thumb.getBlogId());
                                    synchronized (newFilter) {
                                        newFilter.put(key);
                                    }
                                }
                                
                                // 更新进度，定期输出加载进度
                                long currentProcessed = processedCounter.addAndGet(thumbs.size());
                                if (currentBatch % 5 == 0 || currentBatch == numBatches - 1) {
                                    log.info("布隆过滤器数据加载进度: {}/{} ({} %)", 
                                            currentProcessed, total, 
                                            String.format("%.2f", (currentProcessed * 100.0 / total)));
                                }
                            } catch (Exception e) {
                                log.error("加载批次 {} 数据失败", currentBatch, e);
                            }
                        });
                workers.add(worker);
            }
            
            // 等待所有工作线程完成，确保数据完全加载
            for (Thread worker : workers) {
                try {
                    worker.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("等待工作线程被中断", e);
                }
            }
            
            // 使用写锁更新布隆过滤器实例
            lock.writeLock().lock();
            try {
                this.thumbBloomFilter = newFilter;
                log.info("点赞布隆过滤器重建完成，共加载 {} 条记录", processedCounter.get());
            } finally {
                lock.writeLock().unlock();
            }
        } catch (Exception e) {
            log.error("重建布隆过滤器失败", e);
        }
    }
    
    /**
     * 检查用户是否可能对博客进行了点赞
     * @param userId 用户ID
     * @param blogId 博客ID
     * @return 如果返回false则一定不存在，如果返回true则可能存在（有一定误判率）
     */
    public boolean mightExist(Long userId, Long blogId) {
        String key = buildKey(userId, blogId);
        lock.readLock().lock();
        try {
            return thumbBloomFilter.mightContain(key);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 将点赞记录添加到布隆过滤器
     * @param userId 用户ID
     * @param blogId 博客ID
     */
    public void add(Long userId, Long blogId) {
        String key = buildKey(userId, blogId);
        lock.writeLock().lock();
        try {
            thumbBloomFilter.put(key);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 构建布隆过滤器键
     */
    private String buildKey(Long userId, Long blogId) {
        return userId + ":" + blogId;
    }
    
    /**
     * 定时重建布隆过滤器，每天凌晨2点执行
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduledRebuild() {
        log.info("开始执行定时布隆过滤器重建任务");
        recreateBloomFilter();
    }
    
    /**
     * 应用启动后5秒执行一次初始化加载
     */
    @Scheduled(initialDelay = 5000, fixedDelay = Long.MAX_VALUE)
    public void initialLoad() {
        log.info("应用启动完成后，执行布隆过滤器初始化加载");
        recreateBloomFilter();
    }
} 