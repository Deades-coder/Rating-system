package com.yang.ratingsystem.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yang.ratingsystem.model.User;
import jakarta.servlet.http.HttpServletRequest;

/**
* @author Decades
* @description 针对表【user】的数据库操作Service
* @createDate 2025-04-17 18:12:37
*/

public interface UserService extends IService<User> {

    User getLoginUser(HttpServletRequest request);
}
