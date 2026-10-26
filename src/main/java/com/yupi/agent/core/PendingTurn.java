package com.yupi.agent.core;

import com.yupi.agent.llm.model.ChatMessage;
import com.yupi.agent.llm.model.ToolCall;
import com.yupi.agent.model.vo.ToolCallRecordVO;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 等待用户确认的推理现场。
 * <p>
 * 高危工具触发确认时，把当前的对话上下文快照下来存进 Redis；
 * 用户确认后再接着往下跑，而不是从头重来。
 */
@Data
public class PendingTurn implements Serializable {

    private Long sessionId;

    private Long userId;

    private String agentType;

    /**
     * 完整消息列表（含带 tool_calls 的 assistant 消息）
     */
    private List<ChatMessage> messages = new ArrayList<>();

    /**
     * 待确认的工具调用
     */
    private ToolCall pendingCall;

    /**
     * 已经执行过的工具记录
     */
    private List<ToolCallRecordVO> records = new ArrayList<>();

    private int step;

    private int promptTokens;

    private int completionTokens;

    private long startTime;

    private static final long serialVersionUID = 1L;
}
