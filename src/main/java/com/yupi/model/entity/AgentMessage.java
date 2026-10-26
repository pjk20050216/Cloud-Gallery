package com.yupi.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 智能体消息
 */
@TableName(value = "agent_message")
@Data
public class AgentMessage implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 会话 id
     */
    private Long sessionId;

    /**
     * 用户 id
     */
    private Long userId;

    /**
     * user / assistant / tool / system
     */
    private String role;

    /**
     * 产生该消息的智能体
     */
    private String agentType;

    /**
     * 文本内容
     */
    private String content;

    /**
     * 工具名（role=tool）
     */
    private String toolName;

    /**
     * LLM 工具调用 id
     */
    private String toolCallId;

    /**
     * 工具入参 JSON
     */
    private String toolArgs;

    /**
     * 工具出参 JSON
     */
    private String toolResult;

    /**
     * SUCCESS / FAILED / CANCELED
     */
    private String status;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer latencyMs;

    private Date createTime;

    @TableLogic
    private Integer isDelete;

    private static final long serialVersionUID = 1L;
}
