package com.yupi.model.dto.ban;

import lombok.Data;

import java.io.Serializable;

/**
 * 申诉审核请求（管理端）
 */
@Data
public class BanAppealReviewRequest implements Serializable {

    private Long banRecordId;

    /**
     * true=通过（同时解封），false=驳回
     */
    private Boolean approve;

    /**
     * 处理意见
     */
    private String reply;

    private static final long serialVersionUID = 1L;
}
