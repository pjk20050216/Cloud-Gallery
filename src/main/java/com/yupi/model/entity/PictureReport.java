package com.yupi.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@TableName(value = "picture_report")
@Data
public class PictureReport {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long pictureId;

    private Long userId;

    private String reportReason;

    private String reportDetail;

    private Integer status;

    private Long handlerId;

    private String handleResult;

    private Date handleTime;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer isDelete;
}
