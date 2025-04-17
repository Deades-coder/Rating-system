package yang.example.ratingsystem.service;

import yang.example.ratingsystem.model.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;
import yang.example.ratingsystem.model.entity.User;
import yang.example.ratingsystem.model.vo.BlogVO;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

public interface BlogService extends IService<Blog> {

    BlogVO getBlogVOById(long blogId, HttpServletRequest request);

    BlogVO getBlogVO(Blog blog, User loginUser);

    List<BlogVO> getBlogVOList(List<Blog> blogList, HttpServletRequest request);
}
