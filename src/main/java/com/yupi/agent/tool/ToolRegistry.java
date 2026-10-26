package com.yupi.agent.tool;

import com.yupi.agent.llm.model.ToolSpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具注册表：启动时收集所有 {@link AgentTool} Bean。
 */
@Slf4j
@Component
public class ToolRegistry {

    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    public ToolRegistry(List<AgentTool> toolBeans) {
        for (AgentTool tool : toolBeans) {
            AgentTool previous = tools.put(tool.name(), tool);
            if (previous != null) {
                log.warn("工具名重复：{}，后者将覆盖前者", tool.name());
            }
        }
        log.info("智能体工具注册完成，共 {} 个：{}", tools.size(), tools.keySet());
    }

    public AgentTool get(String name) {
        return tools.get(name);
    }

    public Collection<AgentTool> all() {
        return tools.values();
    }

    /**
     * 只暴露指定白名单里的工具，避免模型面对过多选项
     */
    public List<ToolSpec> specsOf(List<String> toolNames) {
        List<ToolSpec> specs = new ArrayList<>();
        if (toolNames == null) {
            return specs;
        }
        for (String name : toolNames) {
            AgentTool tool = tools.get(name);
            if (tool == null) {
                log.warn("智能体引用了不存在的工具：{}", name);
                continue;
            }
            specs.add(ToolSpec.builder()
                    .name(tool.name())
                    .description(tool.description())
                    .parameters(tool.parameters())
                    .build());
        }
        return specs;
    }
}
