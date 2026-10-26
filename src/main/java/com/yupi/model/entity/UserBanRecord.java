package com.yupi.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 用户封禁记录
 */
@TableName(value = "user_ban_record")
@Data
public class UserBanRecord implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 被处罚用户 id
     */
    private Long userId;

    /**
     * 1-封禁 2-禁言 3-限制上传
     */
    private Integer banType;

    /**
     * 原因码
     */
    private String reasonCode;

    /**
     * 详细原因（面向用户展示）
     */
    private String reasonDetail;

    /**
     * 关联对象 id（如被举报图片 id）
     */
    private Long relatedId;

    /**
     * 操作人 id
     */
    private Long operatorId;

    private Date startTime;

    /**
     * 结束时间，为空表示永久
     */
    private Date endTime;

    /**
     * 0-生效中 1-已解封 2-已过期
     */
    private Integer status;

    private String unbanReason;

    /**
     * 0-未申诉 1-申诉中 2-通过 3-驳回
     */
    private Integer appealStatus;

    private String appealContent;

    private Date appealTime;

    private String appealReply;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer isDelete;

    private static final long serialVersionUID = 1L;
}
