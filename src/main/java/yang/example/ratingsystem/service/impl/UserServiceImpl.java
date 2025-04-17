package yang.example.ratingsystem.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import yang.example.ratingsystem.constant.UserConstant;
import yang.example.ratingsystem.model.entity.User;
import yang.example.ratingsystem.service.UserService;
import yang.example.ratingsystem.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;


@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements UserService {

    @Override
    public User getLoginUser(HttpServletRequest request) {
        return (User) request.getSession().getAttribute(UserConstant.LOGIN_USER);
    }

}




