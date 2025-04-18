package com.yang.ratingsystem.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 通用返回类
 * @Author 小小星仔
 * @Create 2025-04-17 18:09
 */
@Data
public class BaseResponse<T> implements Serializable {

    private int code;

    private T data;

    private String message;

    public BaseResponse(int code, T data, String message) {
        this.code = code;
        this.data = data;
        this.message = message;
    }

    public BaseResponse(int code, T data) {
        this(code, data, "");
    }

    public BaseResponse(ErrorCode errorCode) {
        this(errorCode.getCode(), null, errorCode.getMessage());
    }
}
