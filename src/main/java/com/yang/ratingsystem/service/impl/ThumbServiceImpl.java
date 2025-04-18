package com.yang.ratingsystem.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yang.ratingsystem.constant.ThumbConstant;
import com.yang.ratingsystem.manager.cache.CacheManager;
import com.yang.ratingsystem.mapper.ThumbMapper;
import com.yang.ratingsystem.model.Blog;
import com.yang.ratingsystem.model.Thumb;
import com.yang.ratingsystem.model.User;
import com.yang.ratingsystem.model.dto.thumb.DoThumbRequest;
import com.yang.ratingsystem.service.BlogService;
import com.yang.ratingsystem.service.ThumbService;
import com.yang.ratingsystem.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
* @author Decades
* @description 针对表【thumb】的数据库操作Service实现
* @createDate 2025-04-17 18:12:32
*/
@Service("thumbServiceLocalCache")
@Slf4j
@RequiredArgsConstructor
public class ThumbServiceImpl extends ServiceImpl<ThumbMapper, Thumb> implements ThumbService {

    private final UserService userService;

    private final BlogService blogService;

    private final TransactionTemplate transactionTemplate;

    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheManager cacheManager;

    // 是否点赞
    @Override
    public Boolean hasThumb(Long blogId, Long userId) {
//        return redisTemplate.opsForHash().hasKey(ThumbConstant.USER_THUMB_KEY_PREFIX+userId, blogId.toString());
//        return cacheManager.get(ThumbConstant.USER_THUMB_KEY_PREFIX+userId, blogId.toString())!=null;
        Object thumbIdObj = cacheManager.get(ThumbConstant.USER_THUMB_KEY_PREFIX + userId, blogId.toString());
        if (thumbIdObj == null) {
            return false;
        }
        Long thumbId = (Long) thumbIdObj;
        return !thumbId.equals(ThumbConstant.UN_THUMB_CONSTANT);
    }

    @Override
    public Boolean doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        if (doThumbRequest == null || doThumbRequest.getBlogId() == null) {
            throw new RuntimeException("参数错误");
        }
        User loginUser = userService.getLoginUser(request);
        // 加锁
        synchronized (loginUser.getId().toString().intern()) {

            // 编程式事务
            return transactionTemplate.execute(status -> {
                Long blogId = doThumbRequest.getBlogId();
                //mysql点赞实现
//                boolean exists = this.lambdaQuery()
//                        .eq(Thumb::getUserId, loginUser.getId())
//                        .eq(Thumb::getBlogId, blogId)
//                        .exists();
                Boolean exists = this.hasThumb(blogId, loginUser.getId());
                if (exists) {
                    throw new RuntimeException("用户已点赞");
                }

                boolean update = blogService.lambdaUpdate()
                        .eq(Blog::getId, blogId)
                        .setSql("thumbCount = thumbCount + 1")
                        .update();

                Thumb thumb = new Thumb();
                thumb.setUserId(loginUser.getId());
                thumb.setBlogId(blogId);
                // 更新成功才执行
                boolean success = update && this.save(thumb);
                // 点赞记录存入redis
//                if(success){
//                    redisTemplate.opsForHash().put(ThumbConstant.USER_THUMB_KEY_PREFIX+loginUser.getId().toString(),blogId.toString(),thumb.getId());
////                    longRedisTemplate.opsForHash().put(
////                            ThumbConstant.USER_THUMB_KEY_PREFIX + loginUser.getId(),
////                            blogId.toString(),
////                            thumb.getId()
////                    );
//                }
                // 点赞记录存入 Redis
                if (success) {
                    String hashKey = ThumbConstant.USER_THUMB_KEY_PREFIX + loginUser.getId();
                    String fieldKey = blogId.toString();
                    Long realThumbId = thumb.getId();
                    redisTemplate.opsForHash().put(hashKey, fieldKey, realThumbId);
                    cacheManager.putIfPresent(hashKey, fieldKey, realThumbId);
                }

                return success;
            });
        }
    }

    @Override
    public Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        if(doThumbRequest == null || doThumbRequest.getBlogId() == null) {
            throw new RuntimeException("参数错误");
        }
        User loginUser = userService.getLoginUser(request);
        synchronized (loginUser.getId().toString().intern()) {
            return transactionTemplate.execute(status -> {
                Long blogId = doThumbRequest.getBlogId();
                // mysql实现点赞
//                Thumb thumb = this.lambdaQuery().eq(Thumb::getUserId, loginUser.getId())
//                        .eq(Thumb::getBlogId, blogId)
//                        .one();
//                if(thumb == null){
//                    throw new RuntimeException("未点赞");
//                }
//                Long thumbId = ((long) redisTemplate.opsForHash().get(ThumbConstant.USER_THUMB_KEY_PREFIX + loginUser.getId().toString(), blogId.toString()));;
                // 修改这里：先获取Object再转换为Long，这是个bug
//                Object thumbIdObj = redisTemplate.opsForHash().get(ThumbConstant.USER_THUMB_KEY_PREFIX + loginUser.getId().toString(), blogId.toString());
                Object thumbIdObj = cacheManager.get(ThumbConstant.USER_THUMB_KEY_PREFIX + loginUser.getId(), blogId.toString());
                if (thumbIdObj == null) {
                    throw new RuntimeException("用户未点赞");
                }
                Long thumbId = Long.valueOf(thumbIdObj.toString()); // 安全转换
                if (thumbId == null) {
                    throw new RuntimeException("用户未点赞");
                }

                boolean update = blogService.lambdaUpdate().eq(Blog::getId,blogId)
                        .gt(Blog::getThumbCount, 0) // 确保点赞数不会变成负数
                        .setSql("thumbCount = thumbCount - 1")
                        .update();
//                boolean deleteThumb  = update && this.removeById(thumbId);
                boolean deleteThumb = update && this.removeById((Long)thumbIdObj);
                // 点赞记录从redis删除
                if(deleteThumb){
//                    redisTemplate.opsForHash().delete(ThumbConstant.USER_THUMB_KEY_PREFIX + loginUser.getId(),blogId.toString());
                    String hashKey = ThumbConstant.USER_THUMB_KEY_PREFIX + loginUser.getId();
                    String fieldKey = blogId.toString();
                    redisTemplate.opsForHash().delete(hashKey, fieldKey);
                    cacheManager.putIfPresent(hashKey, fieldKey, ThumbConstant.UN_THUMB_CONSTANT);
                }
                return deleteThumb ;
            });
        }
    }
}





