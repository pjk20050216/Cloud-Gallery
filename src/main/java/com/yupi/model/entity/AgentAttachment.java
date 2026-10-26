package com.yupi.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 智能体附件（用户在对话中上传的图片）
 */
@TableName(value = "agent_attachment")
@Data
public class AgentAttachment implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long sessionId;

    /**
     * COS 访问地址
     */
    private String url;

    /**
     * COS 对象 key
     */
    private String cosKey;

    private String fileName;

    private String contentType;

    private Long fileSize;

    /**
     * UPLOADED / CONSUMED
     */
    private String status;

    private Date createTime;

    @TableLogic
    private Integer isDelete;

    private static final long serialVersionUID = 1L;
}
