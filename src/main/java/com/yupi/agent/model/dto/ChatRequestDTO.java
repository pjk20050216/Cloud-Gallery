package com.yupi.agent.model.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 对话请求
 */
@Data
public class ChatRequestDTO implements Serializable {

    /**
     * 会话 id，为空表示新建会话
     */
    private Long sessionId;

    /**
     * 用户输入
     */
    private String message;

    /**
     * 本轮携带的附件 id（先调 /agent/attachment/upload 拿到）
     */
    private List<Long> attachmentIds;

    /**
     * 指定智能体（auto 表示由路由决定）
     */
    private String agentType;

    private static final long serialVersionUID = 1L;
}
