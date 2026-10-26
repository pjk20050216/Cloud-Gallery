package com.yupi.agent.tool;

import cn.hutool.core.util.StrUtil;
import com.yupi.agent.config.AgentProperties;
import com.yupi.agent.core.AgentContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 工具执行前的四道闸。
 * <p>
 * 注意：这里的所有判断都基于 {@link AgentContext} 快照，
 * 不使用 Sa-Token 的 ThreadLocal（异步线程里取不到）。
 */
@Slf4j
@Component
public class ToolGuard {

    @Resource
    private AgentProperties agentProperties;

    /**
     * 已确认的工具调用（一次确认只放行一次）
     */
    public void check(AgentContext context, AgentTool tool, Map<String, Object> args, boolean confirmed) {
        // 闸 1：封禁用户只能只读（或显式声明封禁期间可用的工具，例如"查看处罚记录""提交申诉"）
        if (context.isBanned()
                && tool.risk() != ToolRiskLevel.READ_ONLY
                && !tool.allowedWhenBanned()) {
            throw new IllegalStateException("账号处于封禁状态，暂时只能查询信息，不能执行该操作");
        }

        // 闸 2：工具级权限
        String requiredPermission = tool.requiredPermission();
        if (StrUtil.isNotBlank(requiredPermission)
                && !context.hasPermission(requiredPermission)) {
            throw new IllegalStateException("当前账号没有该操作权限（" + requiredPermission + "）");
        }

        // 闸 3：参数必填校验
        validateRequired(tool, args);

        // 闸 4：高危操作二次确认
        if (tool.risk() == ToolRiskLevel.DANGEROUS && !confirmed) {
            throw new ConfirmRequiredException(tool.name(), args, buildPreview(tool, args));
        }
    }

    /**
     * 只做必填项与基础类型校验，不做完整 JSON Schema 校验（够用且不引入额外依赖）
     */
    @SuppressWarnings("unchecked")
    private void validateRequired(AgentTool tool, Map<String, Object> args) {
        Map<String, Object> schema = tool.parameters();
        if (schema == null) {
            return;
        }
        Object required = schema.get("required");
        if (!(required instanceof Collection)) {
            return;
        }
        List<String> missing = new ArrayList<>();
        for (Object item : (Collection<Object>) required) {
            String key = String.valueOf(item);
            Object value = args == null ? null : args.get(key);
            if (value == null || StrUtil.isBlank(String.valueOf(value))) {
                missing.add(key);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("缺少必填参数：" + String.join(",", missing));
        }
    }

    private String buildPreview(AgentTool tool, Map<String, Object> args) {
        Object description = args == null ? null : args.get("__preview");
        if (description != null) {
            return String.valueOf(description);
        }
        return "即将执行高危操作：" + tool.name() + "，参数 " + args + "，该操作可能不可恢复";
    }

    /**
     * 供 P4 使用：封禁状态
     */
    public boolean isBanned(AgentContext context) {
        return false;
    }
}
