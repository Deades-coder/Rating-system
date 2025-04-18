package com.yang.ratingsystem.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yang.ratingsystem.constant.ThumbConstant;
import com.yang.ratingsystem.mapper.BlogMapper;
import com.yang.ratingsystem.model.Blog;
import com.yang.ratingsystem.model.Thumb;
import com.yang.ratingsystem.model.User;
import com.yang.ratingsystem.model.vo.BlogVO;
import com.yang.ratingsystem.service.BlogService;
import com.yang.ratingsystem.service.ThumbService;
import com.yang.ratingsystem.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
* @author Decades
* @description 针对表【blog】的数据库操作Service实现
* @createDate 2025-04-17 18:12:27
*/
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog>
    implements BlogService{

    @Resource
    private UserService userService;

    @Resource
    @Lazy
    private ThumbService thumbService;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;


    @Override
    public BlogVO getBlogVOById(long blogId, HttpServletRequest request) {
        Blog blog = baseMapper.selectById(blogId);
        User loginUser  = userService.getLoginUser(request);
        return this.getBlogById(blog,loginUser);
    }

    @Override
    public List<BlogVO> getBlogVOList(List<Blog> blogList, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Map<Long, Boolean> blogIdHasThumbMap = new HashMap<>();
        if (ObjUtil.isNotEmpty(loginUser)) {
//            Set<Long> blogIdSet = blogList.stream().map(Blog::getId).collect(Collectors.toSet());
            List<Object> blogIdList = blogList.stream().map(blog -> blog.getId()).collect(Collectors.toList());
            // 获取点赞
//            List<Thumb> thumbList = thumbService.lambdaQuery()
//                    .eq(Thumb::getUserId, loginUser.getId())
//                    .in(Thumb::getBlogId, blogIdSet)
//                    .list();
            List<Object> thumbList = redisTemplate.opsForHash().multiGet(ThumbConstant.USER_THUMB_KEY_PREFIX+loginUser.getId(),blogIdList);
            for (int i = 0; i < thumbList.size(); i++) {
                if(thumbList.get(i)==null){
                    continue;
                }
                blogIdHasThumbMap.put(Long.valueOf(blogIdList.get(i).toString()),true);
            }
//            thumbList.forEach(blogThumb -> blogIdHasThumbMap.put(blogThumb.getBlogId(), true));
        }
        return blogList.stream()
                .map(blog -> {
                    BlogVO blogVO = BeanUtil.copyProperties(blog, BlogVO.class);
                    blogVO.setHasThumb(blogIdHasThumbMap.get(blog.getId()));
                    return blogVO;
                })
                .toList();
    }


    private BlogVO getBlogById(Blog blog, User loginUser) {
        BlogVO blogVO = new BlogVO();
        BeanUtil.copyProperties(blog,blogVO);
        if(loginUser==null){
            return blogVO;
        }
        //mysql
//        Thumb thumb = thumbService.lambdaQuery().eq(Thumb::getUserId,loginUser.getId())
//                .eq(Thumb::getBlogId,blog.getId()).one();
//        blogVO.setHasThumb(thumb!=null);
        //查询blog点赞数量
        Boolean thumbCnt = thumbService.hasThumb(blog.getId(), loginUser.getId());
        blogVO.setHasThumb(thumbCnt);

        return blogVO;
    }
}




