package com.yupi.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/**
 * 封禁记录（脱敏，面向用户）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBanRecordVO implements Serializable {

    private Long banRecordId;

    private Integer banType;

    private String banTypeText;

    private String reasonCode;

    private String reasonDetail;

    private Long relatedId;

    private Date startTime;

    /**
     * 为空表示永久封禁
     */
    private Date endTime;

    private Boolean permanent;

    private Integer status;

    private String statusText;

    private Integer appealStatus;

    private String appealStatusText;

    private String appealContent;

    private String appealReply;

    private Date appealTime;

    private static final long serialVersionUID = 1L;
}
