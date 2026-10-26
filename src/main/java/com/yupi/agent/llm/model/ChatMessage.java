package com.yupi.agent.llm.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 与 LLM 交互的统一消息模型（与具体厂商无关）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage implements Serializable {

    public static final String ROLE_SYSTEM = "system";
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";
    public static final String ROLE_TOOL = "tool";

    /**
     * system / user / assistant / tool
     */
    private String role;

    /**
     * 文本内容
     */
    private String content;

    /**
     * role=tool 时，对应的工具调用 id
     */
    private String toolCallId;

    /**
     * role=tool 时，工具名
     */
    private String name;

    /**
     * role=assistant 时，模型请求的工具调用
     */
    private List<ToolCall> toolCalls;

    public static ChatMessage system(String content) {
        return ChatMessage.builder().role(ROLE_SYSTEM).content(content).build();
    }

    public static ChatMessage user(String content) {
        return ChatMessage.builder().role(ROLE_USER).content(content).build();
    }

    public static ChatMessage assistant(String content) {
        return ChatMessage.builder().role(ROLE_ASSISTANT).content(content).build();
    }

    public static ChatMessage assistantWithTools(String content, List<ToolCall> toolCalls) {
        return ChatMessage.builder().role(ROLE_ASSISTANT).content(content).toolCalls(toolCalls).build();
    }

    public static ChatMessage tool(String toolCallId, String name, String content) {
        return ChatMessage.builder()
                .role(ROLE_TOOL)
                .toolCallId(toolCallId)
                .name(name)
                .content(content)
                .build();
    }
}
