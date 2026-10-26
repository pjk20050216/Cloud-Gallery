package com.yupi.agent.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 智能体能力信息（给前端渲染引导用）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentInfoVO implements Serializable {

    private String name;

    private String displayName;

    private String description;

    /**
     * 该智能体可用的工具
     */
    private List<String> tools;

    private static final long serialVersionUID = 1L;
}
