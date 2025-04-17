package yang.example.ratingsystem.service;

import yang.example.ratingsystem.model.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.servlet.http.HttpServletRequest;

public interface UserService extends IService<User> {

    User getLoginUser(HttpServletRequest request);
}
