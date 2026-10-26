package com.yupi.agent.llm.model;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 一次 LLM 调用的请求
 */
@Data
@Builder
public class ChatRequest implements Serializable {

    private String model;

    private List<ChatMessage> messages;

    /**
     * 可用工具，可为空
     */
    private List<ToolSpec> tools;

    private Double temperature;

    private Integer maxTokens;

    private Integer timeoutSeconds;
}
