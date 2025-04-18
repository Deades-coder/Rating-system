package com.yang.ratingsystem.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yang.ratingsystem.model.Blog;
import com.yang.ratingsystem.model.vo.BlogVO;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
* @author Decades
* @description 针对表【blog】的数据库操作Service
* @createDate 2025-04-17 18:12:27
*/
public interface BlogService extends IService<Blog> {
    BlogVO getBlogVOById(long blogId, HttpServletRequest request);
    List<BlogVO> getBlogVOList(List<Blog> blogList, HttpServletRequest request);


}
