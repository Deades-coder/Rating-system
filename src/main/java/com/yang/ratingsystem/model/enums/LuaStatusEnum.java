package com.yang.ratingsystem.model.enums;

import lombok.Getter;

/**
 * @Author 小小星仔
 * @Create 2025-04-17 22:32
 */
@Getter
public enum LuaStatusEnum {
    // 成功
    SUCCESS(1L),
    // 失败
    FAIL(-1L),
    ;

    private final long value;

    LuaStatusEnum(long value) {
        this.value = value;
    }

}

