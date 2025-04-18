package com.yang.ratingsystem.utils;

import com.yang.ratingsystem.constant.ThumbConstant;

/**
 * @Author 小小星仔
 * @Create 2025-04-17 22:25
 */
public class RedisKeyUtil {

    public static String getUserThumbKey(Long userId) {
        return ThumbConstant.USER_THUMB_KEY_PREFIX + userId;
    }

    /**
     * 获取 临时点赞记录 key
     */
    public static String getTempThumbKey(String time) {
        return ThumbConstant.TEMP_THUMB_KEY_PREFIX.formatted(time);
    }

}
