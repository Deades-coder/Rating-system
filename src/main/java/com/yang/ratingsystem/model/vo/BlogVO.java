package com.yang.ratingsystem.model.vo;

import lombok.Data;

import java.util.Date;

/**
 * @Author 小小星仔
 * @Create 2025-04-17 19:39
 */
@Data
public class BlogVO {

    private Long id;

    /**
     * 标题
     */
    private String title;

    /**
     * 封面
     */
    private String coverImg;

    /**
     * 内容
     */
    private String content;

    /**
     * 点赞数
     */
    private Integer thumbCount;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 是否已点赞
     */
    private Boolean hasThumb;

}
