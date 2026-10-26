package com.yupi.model.dto.ban;

import lombok.Data;

import java.io.Serializable;

/**
 * 封禁用户请求（管理端）
 */
@Data
public class UserBanRequest implements Serializable {

    private Long userId;

    /**
     * 1-封禁 2-禁言 3-限制上传
     */
    private Integer banType = 1;

    /**
     * 原因码：VIOLENT_CONTENT / SPAM / COPYRIGHT / OTHER
     */
    private String reasonCode;

    /**
     * 详细原因（会展示给用户，请写清楚）
     */
    private String reasonDetail;

    /**
     * 关联对象 id（例如被举报的图片 id）
     */
    private Long relatedId;

    /**
     * 封禁天数；为空或 <=0 表示永久
     */
    private Integer durationDays;

    private static final long serialVersionUID = 1L;
}
