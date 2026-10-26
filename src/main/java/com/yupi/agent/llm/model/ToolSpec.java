package com.yupi.agent.llm.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 暴露给模型的工具描述（JSON Schema）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolSpec implements Serializable {

    private String name;

    private String description;

    /**
     * JSON Schema：{"type":"object","properties":{...},"required":[...]}
     */
    private Map<String, Object> parameters;
}
