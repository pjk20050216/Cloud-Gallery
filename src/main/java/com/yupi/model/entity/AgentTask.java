package com.yupi.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 智能体异步任务
 */
@TableName(value = "agent_task")
@Data
public class AgentTask implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 业务任务号（对外暴露）
     */
    private String taskId;

    private Long userId;

    private Long sessionId;

    /**
     * CHAT / BATCH_UPLOAD
     */
    private String type;

    /**
     * PENDING / RUNNING / SUCCESS / FAILED / CANCELED
     */
    private String status;

    /**
     * 0-100
     */
    private Integer progress;

    private String progressMessage;

    private String inputJson;

    private String outputJson;

    private String errorMsg;

    private Date createTime;

    private Date startTime;

    private Date endTime;

    private Date updateTime;

    private static final long serialVersionUID = 1L;
}
