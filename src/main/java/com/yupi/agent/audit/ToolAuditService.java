package com.yupi.agent.audit;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.yupi.agent.core.AgentContext;
import com.yupi.agent.tool.AgentTool;
import com.yupi.agent.tool.ToolResult;
import com.yupi.mapper.AgentToolAuditMapper;
import com.yupi.model.entity.AgentToolAudit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;
import java.util.Map;

/**
 * 工具调用审计。落库失败不影响主流程。
 */
@Slf4j
@Service
public class ToolAuditService {

    @Resource
    private AgentToolAuditMapper agentToolAuditMapper;

    public void record(AgentContext context, Long sessionId, String agentType,
                       AgentTool tool, Map<String, Object> args,
                       ToolResult result, int costMs) {
        try {
            AgentToolAudit audit = new AgentToolAudit();
            audit.setTraceId(context.getTraceId());
            audit.setSessionId(sessionId);
            audit.setUserId(context.getUserId());
            audit.setAgentType(agentType);
            audit.setToolName(tool.name());
            audit.setRiskLevel(tool.risk() == null ? null : tool.risk().name());
            audit.setArgsJson(safeJson(args));
            audit.setResultJson(StrUtil.maxLength(result == null ? null : safeJson(result), 2000));
            audit.setSuccess(result != null && result.isSuccess() ? 1 : 0);
            audit.setErrorMsg(StrUtil.maxLength(result == null ? "无返回" : result.getError(), 1000));
            audit.setCostMs(costMs);
            audit.setCreateTime(new Date());
            agentToolAuditMapper.insert(audit);
        } catch (Exception e) {
            log.warn("写入工具审计失败：{}", e.getMessage());
        }
    }

    private String safeJson(Object value) {
        try {
            return JSONUtil.toJsonStr(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }
}
