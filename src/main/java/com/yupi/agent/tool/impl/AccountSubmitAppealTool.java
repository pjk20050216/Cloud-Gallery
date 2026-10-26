package com.yupi.agent.tool.impl;

import com.yupi.agent.core.AgentContext;
import com.yupi.agent.tool.AbstractAgentTool;
import com.yupi.agent.tool.ToolNames;
import com.yupi.agent.tool.ToolResult;
import com.yupi.agent.tool.ToolRiskLevel;
import com.yupi.exception.BusinessException;
import com.yupi.service.UserBanService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 提交封禁申诉。
 * <p>
 * 这是封禁用户唯一被允许的写操作，所以 {@link #allowedWhenBanned()} 返回 true。
 * 但仍然要求用户明确同意（ToolGuard 的必填校验会保证 content 非空）。
 */
@Component
public class AccountSubmitAppealTool extends AbstractAgentTool {

    @Resource
    private UserBanService userBanService;

    @Override
    public String name() {
        return ToolNames.ACCOUNT_SUBMIT_APPEAL;
    }

    @Override
    public String description() {
        return "为当前账号的某条处罚记录提交申诉。"
                + "当用户明确表达“我要申诉”“帮我申诉一下”，并说明了理由时调用。"
                + "调用前必须先通过 account.myBanRecords 拿到 banRecordId，并向用户确认申诉内容。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> properties = properties();
        properties.put("banRecordId", prop("integer", "处罚记录 id，从 account.myBanRecords 获取"));
        properties.put("content", prop("string", "申诉理由，要写清楚为什么认为处罚有误"));
        return schema(properties, "banRecordId", "content");
    }

    @Override
    public ToolRiskLevel risk() {
        return ToolRiskLevel.WRITE;
    }

    /**
     * 封禁用户也能申诉，否则这个功能就没意义
     */
    @Override
    public boolean allowedWhenBanned() {
        return true;
    }

    @Override
    public ToolResult execute(AgentContext context, Map<String, Object> args) {
        Long banRecordId = asLong(args.get("banRecordId"));
        String content = trimToNull(args.get("content"));
        if (banRecordId == null) {
            return ToolResult.fail("处罚记录 id 不能为空");
        }
        if (content == null) {
            return ToolResult.fail("申诉理由不能为空");
        }

        try {
            boolean ok = userBanService.submitAppeal(context.getUserId(), banRecordId, content);
            if (!ok) {
                return ToolResult.fail("申诉提交失败，请稍后重试");
            }
        } catch (BusinessException e) {
            return ToolResult.fail(e.getMessage());
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("banRecordId", banRecordId);
        data.put("status", "申诉中");
        return ToolResult.ok(data, "申诉已提交，管理员会尽快处理");
    }
}
