package com.yang.ratingsystem.controller;

import com.yang.ratingsystem.common.BaseResponse;
import com.yang.ratingsystem.common.ResultUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用于测试连接的控制器
 */
@RestController
public class PingController {

    /**
     * 简单的ping测试端点
     */
    @GetMapping("/ping")
    public BaseResponse<String> ping() {
        return ResultUtils.success("pong");
    }
} 