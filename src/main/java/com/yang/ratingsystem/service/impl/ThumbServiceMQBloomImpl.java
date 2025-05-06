package com.yang.ratingsystem.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yang.ratingsystem.constant.RedisLuaScriptConstant;
import com.yang.ratingsystem.constant.ThumbConstant;
import com.yang.ratingsystem.filter.BloomFilterService;
import com.yang.ratingsystem.listener.thumb.msg.ThumbEvent;
import com.yang.ratingsystem.mapper.ThumbMapper;
import com.yang.ratingsystem.model.Thumb;
import com.yang.ratingsystem.model.User;
import com.yang.ratingsystem.model.dto.thumb.DoThumbRequest;
import com.yang.ratingsystem.model.enums.LuaStatusEnum;
import com.yang.ratingsystem.service.ThumbService;
import com.yang.ratingsystem.service.UserService;
import com.yang.ratingsystem.utils.RedisKeyUtil;
import com.yang.ratingsystem.manager.cache.CacheManager;
import com.github.benmanes.caffeine.cache.Cache;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClientException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Lazy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
* 基于消息队列+布隆过滤器的点赞Service实现
* 
* 相比传统实现，具有以下优势：
* 1. 使用布隆过滤器进行快速判断，减少Redis访问，提高性能
* 2. 使用消息队列异步处理点赞数据持久化，提高响应速度
* 3. 利用虚拟线程处理消息发送和布隆过滤器更新，降低资源占用
* 4. 实现了Redis与数据库的最终一致性
*/
@Service("thumbServiceMQBloom")
@Slf4j
public class ThumbServiceMQBloomImpl extends ServiceImpl<ThumbMapper, Thumb>
        implements ThumbService {

    private final UserService userService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final Producer<ThumbEvent> thumbEventProducer;
    private final BloomFilterService bloomFilterService;
    private final CacheManager cacheManager;
    private final Cache<String, Boolean> localThumbCache;
    
    @Value("${pulsar.topic:thumb-topic}")
    private String thumbTopic;
    
    public ThumbServiceMQBloomImpl(
            UserService userService,
            RedisTemplate<String, Object> redisTemplate,
            Producer<ThumbEvent> thumbEventProducer,
            @Lazy BloomFilterService bloomFilterService,
            CacheManager cacheManager) {
        this.userService = userService;
        this.redisTemplate = redisTemplate;
        this.thumbEventProducer = thumbEventProducer;
        this.bloomFilterService = bloomFilterService;
        this.cacheManager = cacheManager;
        
        // 初始化本地缓存
        this.localThumbCache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .weakKeys()
                .weakValues()
                .recordStats()
                .build();
    }

    /**
     * 执行点赞操作
     * 
     * 实现流程：
     * 1. 先使用布隆过滤器快速判断用户是否已点赞
     * 2. 通过Redis缓存进一步验证
     * 3. 使用Lua脚本在Redis中记录点赞
     * 4. 通过虚拟线程异步发送点赞消息到消息队列
     * 5. 异步更新布隆过滤器
     * 
     * @param doThumbRequest 点赞请求对象
     * @param request HTTP请求
     * @return 点赞结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        if (doThumbRequest == null || doThumbRequest.getBlogId() == null) {
            throw new RuntimeException("参数错误");
        }
        
        User loginUser = userService.getLoginUser(request);
        Long loginUserId = loginUser.getId();
        Long blogId = doThumbRequest.getBlogId();
        String userThumbKey = RedisKeyUtil.getUserThumbKey(loginUserId);
        
        // 使用布隆过滤器进行快速判断，避免缓存穿透
        if (bloomFilterService.mightExist(loginUserId, blogId)) {
            // 再次检查Redis缓存，布隆过滤器可能有误判
            if (Boolean.TRUE.equals(redisTemplate.opsForHash().hasKey(userThumbKey, blogId.toString()))) {
                log.info("用户已点赞 (通过布隆过滤器快速判断): userId={}, blogId={}", loginUserId, blogId);
                throw new RuntimeException("用户已点赞");
            }
            log.debug("布隆过滤器判断可能存在，但Redis中不存在: userId={}, blogId={}", loginUserId, blogId);
        }
        
        try {
            // 使用Lua脚本原子性地在Redis中记录点赞
            // 通过Lua脚本确保操作的原子性和幂等性
            long result = redisTemplate.execute(
                    RedisLuaScriptConstant.THUMB_SCRIPT_MQ,
                    List.of(userThumbKey),
                    blogId
            );
            if (LuaStatusEnum.FAIL.getValue() == result) {
                throw new RuntimeException("用户已点赞");
            }

            // 构建点赞事件消息
            ThumbEvent thumbEvent = ThumbEvent.builder()
                    .blogId(blogId)
                    .userId(loginUserId)
                    .type(ThumbEvent.EventType.INCR)
                    .eventTime(LocalDateTime.now())
                    .build();
            
            // 使用虚拟线程异步发送消息，但同步等待结果
            // 确保消息发送成功，失败时可以回滚Redis操作
            try {
                MessageId messageId = Executors.newVirtualThreadPerTaskExecutor()
                        .submit(() -> {
                            try {
                                return thumbEventProducer.send(thumbEvent);
                            } catch (Exception e) {
                                log.error("在虚拟线程中发送点赞消息失败: userId={}, blogId={}", loginUserId, blogId, e);
                                throw e;
                            }
                        })
                        .get();
                
                log.info("点赞消息发送成功: userId={}, blogId={}, messageId={}", 
                        loginUserId, blogId, messageId);
                
                // 使用虚拟线程异步更新布隆过滤器，无需等待完成
                // 这里使用fire-and-forget模式，不影响主流程
                Thread.ofVirtual()
                        .name("bloom-updater-" + loginUserId + "-" + blogId)
                        .start(() -> {
                            try {
                                bloomFilterService.add(loginUserId, blogId);
                                log.debug("布隆过滤器更新成功: userId={}, blogId={}", loginUserId, blogId);
                            } catch (Exception e) {
                                log.warn("更新布隆过滤器失败: userId={}, blogId={}", loginUserId, blogId, e);
                                // 布隆过滤器更新失败不影响主流程，将在定时任务中重建
                            }
                        });
            } catch (Exception e) {
                // 发送失败时，从Redis中删除点赞记录，保持一致性
                redisTemplate.opsForHash().delete(userThumbKey, blogId.toString());
                log.error("点赞事件发送失败: userId={}, blogId={}", loginUserId, blogId, e);
                throw new RuntimeException("点赞操作失败，请稍后重试", e);
            }
            
            return true;
        } catch (Exception e) {
            log.error("点赞操作失败: userId={}, blogId={}", loginUserId, blogId, e);
            // 确保Redis中没有点赞记录，避免不一致状态
            redisTemplate.opsForHash().delete(userThumbKey, blogId.toString());
            throw e;
        }
    }

    /**
     * 取消点赞操作
     * 
     * 实现流程：
     * 1. 使用布隆过滤器快速判断用户是否已点赞
     * 2. 通过Lua脚本从Redis中删除点赞记录
     * 3. 通过虚拟线程异步发送取消点赞消息
     * 
     * @param doThumbRequest 取消点赞请求
     * @param request HTTP请求
     * @return 取消点赞结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        if (doThumbRequest == null || doThumbRequest.getBlogId() == null) {
            throw new RuntimeException("参数错误");
        }
        User loginUser = userService.getLoginUser(request);
        Long loginUserId = loginUser.getId();
        Long blogId = doThumbRequest.getBlogId();
        String userThumbKey = RedisKeyUtil.getUserThumbKey(loginUserId);
        
        // 使用布隆过滤器快速判断用户是否可能已点赞
        // 由于布隆过滤器不会漏报，所以如果返回false，用户肯定未点赞
        if (!bloomFilterService.mightExist(loginUserId, blogId)) {
            log.info("用户未点赞 (通过布隆过滤器快速判断): userId={}, blogId={}", loginUserId, blogId);
            throw new RuntimeException("用户未点赞");
        }
        
        try {
            // 通过Lua脚本原子性地从Redis中删除点赞记录
            long result = redisTemplate.execute(
                    RedisLuaScriptConstant.UNTHUMB_SCRIPT_MQ,
                    List.of(userThumbKey),
                    blogId
            );
            if (LuaStatusEnum.FAIL.getValue() == result) {
                throw new RuntimeException("用户未点赞");
            }
            
            // 构建取消点赞事件消息
            ThumbEvent thumbEvent = ThumbEvent.builder()
                    .blogId(blogId)
                    .userId(loginUserId)
                    .type(ThumbEvent.EventType.DECR)
                    .eventTime(LocalDateTime.now())
                    .build();
            
            // 使用虚拟线程异步发送消息，但同步等待结果
            try {
                MessageId messageId = Executors.newVirtualThreadPerTaskExecutor()
                        .submit(() -> {
                            try {
                                return thumbEventProducer.send(thumbEvent);
                            } catch (Exception e) {
                                log.error("在虚拟线程中发送取消点赞消息失败: userId={}, blogId={}", loginUserId, blogId, e);
                                throw e;
                            }
                        })
                        .get();
                
                log.info("取消点赞消息发送成功: userId={}, blogId={}, messageId={}", 
                        loginUserId, blogId, messageId);
                
                // 布隆过滤器不支持删除操作，将在定时任务中重建
                // 这是布隆过滤器的局限性，需要通过定期重建来解决
            } catch (Exception e) {
                // 发送失败时，恢复Redis中的点赞记录，保持一致性
                redisTemplate.opsForHash().put(userThumbKey, blogId.toString(), true);
                log.error("取消点赞事件发送失败: userId={}, blogId={}", loginUserId, blogId, e);
                throw new RuntimeException("取消点赞操作失败，请稍后重试", e);
            }
            
            return true;
        } catch (Exception e) {
            log.error("取消点赞操作失败: userId={}, blogId={}", loginUserId, blogId, e);
            // 确保Redis中恢复点赞记录，避免不一致状态
            redisTemplate.opsForHash().put(userThumbKey, blogId.toString(), true);
            throw e;
        }
    }

    /**
     * 查询用户是否对博客点赞
     * 使用布隆过滤器+Redis缓存实现高效查询
     * 
     * @param blogId 博客ID
     * @param userId 用户ID
     * @return 是否已点赞
     */
    @Override
    public Boolean hasThumb(Long blogId, Long userId) {
        String cacheKey = buildCacheKey(userId, blogId);
        
        // 1. 先查本地缓存
        Boolean cached = localThumbCache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }

        // 2. 使用布隆过滤器快速判断
        if (!bloomFilterService.mightExist(userId, blogId)) {
            localThumbCache.put(cacheKey, false);
            return false;
        }

        // 3. 查询Redis确认
        boolean exists = redisTemplate.opsForHash()
                .hasKey(RedisKeyUtil.getUserThumbKey(userId), blogId.toString());
        
        // 更新本地缓存
        localThumbCache.put(cacheKey, exists);
        return exists;
    }

    private String buildCacheKey(Long userId, Long blogId) {
        return userId + ":" + blogId;
    }
}