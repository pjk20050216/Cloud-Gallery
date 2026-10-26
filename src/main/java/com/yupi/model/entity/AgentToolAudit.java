package com.yupi.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 智能体工具调用审计
 */
@TableName(value = "agent_tool_audit")
@Data
public class AgentToolAudit implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String traceId;

    private Long sessionId;

    private String taskId;

    private Long userId;

    private String agentType;

    private String toolName;

    private String riskLevel;

    private String argsJson;

    private String resultJson;

    private Integer success;

    private String errorMsg;

    private Integer costMs;

    private Date createTime;

    private static final long serialVersionUID = 1L;
}
