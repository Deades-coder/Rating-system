package com.yang.ratingsystem.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yang.ratingsystem.constant.UserConstant;
import com.yang.ratingsystem.mapper.UserMapper;
import com.yang.ratingsystem.model.User;
import com.yang.ratingsystem.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

/**
* @author Decades
* @description 针对表【user】的数据库操作Service实现
* @createDate 2025-04-17 18:12:37
*/
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
    implements UserService{

    @Override
    public User getLoginUser(HttpServletRequest request) {
        HttpSession session = request.getSession();
        User user = (User) session.getAttribute(UserConstant.LOGIN_USER);
        return user;
    }
}




