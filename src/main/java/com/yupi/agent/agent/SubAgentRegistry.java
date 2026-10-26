package com.yupi.agent.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 子智能体注册表
 */
@Slf4j
@Component
public class SubAgentRegistry {

    public static final String DEFAULT_AGENT = "general";

    private final Map<String, SubAgent> agents = new LinkedHashMap<>();

    private final SubAgent defaultAgent;

    public SubAgentRegistry(List<SubAgent> agentBeans) {
        for (SubAgent agent : agentBeans) {
            agents.put(agent.name(), agent);
        }
        SubAgent fallback = agents.get(DEFAULT_AGENT);
        if (fallback == null && !agentBeans.isEmpty()) {
            fallback = agentBeans.get(0);
        }
        this.defaultAgent = fallback;
        log.info("智能体注册完成，共 {} 个：{}", agents.size(), agents.keySet());
    }

    public SubAgent get(String name) {
        return agents.get(name);
    }

    public SubAgent getDefault() {
        return defaultAgent;
    }

    public Collection<SubAgent> all() {
        return agents.values();
    }
}
