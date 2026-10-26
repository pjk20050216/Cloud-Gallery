package com.yupi.agent.llm.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 模型请求的一次工具调用
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCall implements Serializable {

    /**
     * 模型生成的调用 id，回灌 tool 消息时必须带上
     */
    private String id;

    /**
     * 工具名
     */
    private String name;

    /**
     * 工具入参，原始 JSON 字符串（由模型生成，视为不可信输入）
     */
    private String arguments;
}
