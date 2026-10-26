package com.yupi.agent.llm.model;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 一次 LLM 调用的响应
 */
@Data
@Builder
public class ChatResponse implements Serializable {

    private String content;

    /**
     * 模型请求调用的工具（为空表示这是最终回答）
     */
    private List<ToolCall> toolCalls;

    private String finishReason;

    private int promptTokens;

    private int completionTokens;

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
