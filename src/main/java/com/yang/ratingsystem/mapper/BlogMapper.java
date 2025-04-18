package com.yang.ratingsystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yang.ratingsystem.model.Blog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

/**
* @author Decades
* @description 针对表【blog】的数据库操作Mapper
* @createDate 2025-04-17 18:12:27
* @Entity com.yang.ratingsystem.model.Blog
*/

public interface BlogMapper extends BaseMapper<Blog> {
    //批量更新sql
    void batchUpdateThumbCount(@Param("countMap") Map<Long, Long> countMap);
}





