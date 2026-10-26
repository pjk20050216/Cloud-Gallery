package com.yupi.agent.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 一次对话的完整结果（非流式接口返回）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponseVO implements Serializable {

    private Long sessionId;

    private Long messageId;

    private String agentType;

    private String agentDisplayName;

    private String content;

    private List<ToolCallRecordVO> toolCalls;

    private Integer promptTokens;

    private Integer completionTokens;

    private Long latencyMs;

    /**
     * 是否停在"等待用户确认高危操作"的状态
     */
    private Boolean pendingConfirm;

    private static final long serialVersionUID = 1L;
}
