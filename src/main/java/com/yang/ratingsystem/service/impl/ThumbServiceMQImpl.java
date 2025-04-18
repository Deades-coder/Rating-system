package com.yang.ratingsystem.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yang.ratingsystem.constant.RedisLuaScriptConstant;
import com.yang.ratingsystem.constant.ThumbConstant;
import com.yang.ratingsystem.listener.thumb.msg.ThumbEvent;
import com.yang.ratingsystem.manager.cache.CacheManager;
import com.yang.ratingsystem.mapper.ThumbMapper;
import com.yang.ratingsystem.model.Blog;
import com.yang.ratingsystem.model.Thumb;
import com.yang.ratingsystem.model.User;
import com.yang.ratingsystem.model.dto.thumb.DoThumbRequest;
import com.yang.ratingsystem.model.enums.LuaStatusEnum;
import com.yang.ratingsystem.service.BlogService;
import com.yang.ratingsystem.service.ThumbService;
import com.yang.ratingsystem.service.UserService;
import com.yang.ratingsystem.utils.RedisKeyUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClientException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
* @author Decades
* @description 针对表【thumb】的数据库操作Service实现
* @createDate 2025-04-17 18:12:32
*/
@Service("thumbService")
@Slf4j
@RequiredArgsConstructor
public class ThumbServiceMQImpl extends ServiceImpl<ThumbMapper, Thumb>
        implements ThumbService {

    private final UserService userService;

    private final RedisTemplate<String, Object> redisTemplate;

    private final Producer<ThumbEvent> thumbEventProducer;
    
    @Value("${pulsar.topic:thumb-topic}")
    private String thumbTopic;

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
        
        try {
            // 执行 Lua 脚本，点赞存入 Redis
            long result = redisTemplate.execute(
                    RedisLuaScriptConstant.THUMB_SCRIPT_MQ,
                    List.of(userThumbKey),
                    blogId
            );
            if (LuaStatusEnum.FAIL.getValue() == result) {
                throw new RuntimeException("用户已点赞");
            }

            ThumbEvent thumbEvent = ThumbEvent.builder()
                    .blogId(blogId)
                    .userId(loginUserId)
                    .type(ThumbEvent.EventType.INCR)
                    .eventTime(LocalDateTime.now())
                    .build();
            
            // 发送消息到Pulsar
            try {
                MessageId messageId = thumbEventProducer.send(thumbEvent);
                log.info("点赞消息发送成功: userId={}, blogId={}, messageId={}", 
                        loginUserId, blogId, messageId);
            } catch (PulsarClientException e) {
                // 发送失败时，从Redis中删除点赞记录
                redisTemplate.opsForHash().delete(userThumbKey, blogId.toString());
                log.error("点赞事件发送失败: userId={}, blogId={}", loginUserId, blogId, e);
                throw new RuntimeException("点赞操作失败，请稍后重试");
            }
            
            return true;
        } catch (Exception e) {
            log.error("点赞操作失败: userId={}, blogId={}", loginUserId, blogId, e);
            // 确保Redis中没有点赞记录
            redisTemplate.opsForHash().delete(userThumbKey, blogId.toString());
            throw e;
        }
    }

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
        
        try {
            // 执行 Lua 脚本，点赞记录从 Redis 删除
            long result = redisTemplate.execute(
                    RedisLuaScriptConstant.UNTHUMB_SCRIPT_MQ,
                    List.of(userThumbKey),
                    blogId
            );
            if (LuaStatusEnum.FAIL.getValue() == result) {
                throw new RuntimeException("用户未点赞");
            }
            
            ThumbEvent thumbEvent = ThumbEvent.builder()
                    .blogId(blogId)
                    .userId(loginUserId)
                    .type(ThumbEvent.EventType.DECR)
                    .eventTime(LocalDateTime.now())
                    .build();
            
            // 发送消息到Pulsar
            try {
                MessageId messageId = thumbEventProducer.send(thumbEvent);
                log.info("取消点赞消息发送成功: userId={}, blogId={}, messageId={}", 
                        loginUserId, blogId, messageId);
            } catch (PulsarClientException e) {
                // 发送失败时，恢复Redis中的点赞记录
                redisTemplate.opsForHash().put(userThumbKey, blogId.toString(), true);
                log.error("取消点赞事件发送失败: userId={}, blogId={}", loginUserId, blogId, e);
                throw new RuntimeException("取消点赞操作失败，请稍后重试");
            }
            
            return true;
        } catch (Exception e) {
            log.error("取消点赞操作失败: userId={}, blogId={}", loginUserId, blogId, e);
            // 确保Redis中恢复点赞记录
            redisTemplate.opsForHash().put(userThumbKey, blogId.toString(), true);
            throw e;
        }
    }

    @Override
    public Boolean hasThumb(Long blogId, Long userId) {
        return redisTemplate.opsForHash().hasKey(RedisKeyUtil.getUserThumbKey(userId), blogId.toString());
    }
}






