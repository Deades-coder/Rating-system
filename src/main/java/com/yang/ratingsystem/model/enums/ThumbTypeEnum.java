package com.yang.ratingsystem.model.enums;

import lombok.Getter;

/**
 * @Author 小小星仔
 * @Create 2025-04-17 22:29
 */
@Getter
public enum ThumbTypeEnum {
    // 点赞
    INCR(1),
    // 取消点赞
    DECR(-1),
    // 不发生改变
    NON(0),
    ;

    private final int value;

    ThumbTypeEnum(int value) {
        this.value = value;
    }

}
