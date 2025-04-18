package com.yang.ratingsystem.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yang.ratingsystem.model.Thumb;
import com.yang.ratingsystem.model.dto.thumb.DoThumbRequest;
import jakarta.servlet.http.HttpServletRequest;

/**
* @author Decades
* @description 针对表【thumb】的数据库操作Service
* @createDate 2025-04-17 18:12:32
*/
public interface ThumbService extends IService<Thumb> {
    /**
     * 点赞
     * @param doThumbRequest
     * @param request
     * @return {@link Boolean }
     */
    Boolean doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request);


    /**
     * 取消点赞
     * @param doThumbRequest
     * @param request
     * @return {@link Boolean }
     */
    Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request);

    Boolean hasThumb(Long blogId, Long userId);


}
