package com.yupi.agent.tool.impl;

import com.yupi.agent.core.AgentContext;
import com.yupi.agent.tool.AbstractAgentTool;
import com.yupi.agent.tool.ToolNames;
import com.yupi.agent.tool.ToolResult;
import com.yupi.agent.tool.ToolRiskLevel;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 智能体转交工具（虚拟工具）。
 * <p>
 * 真正执行由 {@code AgentOrchestrator} 拦截处理：切换当前智能体并重新调用模型。
 * 这里只负责把工具描述暴露给模型。
 */
@Component
public class HandoffTool extends AbstractAgentTool {

    @Override
    public String name() {
        return ToolNames.HANDOFF;
    }

    @Override
    public String description() {
        return "把当前对话转交给更合适的智能体。当用户的问题不属于你的职责范围时调用。"
                + "可选目标：picture（图片上传/搜索/编辑/删除）、"
                + "space（空间容量/额度/统计）、"
                + "account（账号状态/封禁/申诉/余额）、"
                + "general（闲聊与兜底）。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> properties = properties();
        properties.put("to", prop("string", "目标智能体：picture / space / account / general"));
        properties.put("reason", prop("string", "转交原因，一句话"));
        return schema(properties, "to");
    }

    @Override
    public ToolRiskLevel risk() {
        return ToolRiskLevel.READ_ONLY;
    }

    /**
     * 不会被真正执行（编排器拦截），保留实现以便单测直接调用
     */
    @Override
    public ToolResult execute(AgentContext context, Map<String, Object> args) {
        String to = trimToNull(args.get("to"));
        return ToolResult.ok(to, "转交给 " + to);
    }
}
