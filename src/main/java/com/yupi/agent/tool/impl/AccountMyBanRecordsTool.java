package com.yupi.agent.tool.impl;

import com.yupi.agent.core.AgentContext;
import com.yupi.agent.tool.AbstractAgentTool;
import com.yupi.agent.tool.ToolNames;
import com.yupi.agent.tool.ToolResult;
import com.yupi.agent.tool.ToolRiskLevel;
import com.yupi.model.entity.UserBanRecord;
import com.yupi.model.vo.UserBanRecordVO;
import com.yupi.service.UserBanService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 查询当前用户自己的处罚记录（只读）。
 * <p>
 * 封禁用户也能用，这是"我为什么被封"的核心数据来源。
 */
@Component
public class AccountMyBanRecordsTool extends AbstractAgentTool {

    @Resource
    private UserBanService userBanService;

    @Override
    public String name() {
        return ToolNames.ACCOUNT_MY_BAN_RECORDS;
    }

    @Override
    public String description() {
        return "查询当前账号的全部处罚记录，包含处罚类型、原因、生效时间、解封时间、申诉状态。"
                + "当用户问“我为什么被封”“我什么时候能解封”“我的申诉怎么样了”时调用。"
                + "只能查自己的记录。";
    }

    @Override
    public Map<String, Object> parameters() {
        return schema(properties());
    }

    @Override
    public ToolRiskLevel risk() {
        return ToolRiskLevel.READ_ONLY;
    }

    @Override
    public ToolResult execute(AgentContext context, Map<String, Object> args) {
        List<UserBanRecord> records = userBanService.listByUser(context.getUserId());
        if (records.isEmpty()) {
            return ToolResult.ok(new java.util.ArrayList<>(), "当前账号没有任何处罚记录");
        }

        List<UserBanRecordVO> list = records.stream()
                .map(userBanService::toVO)
                .collect(Collectors.toList());

        UserBanRecord active = records.stream()
                .filter(r -> Integer.valueOf(0).equals(r.getStatus()))
                .findFirst()
                .orElse(null);

        String summary;
        if (active != null) {
            summary = String.format("当前有 %d 条处罚记录，其中 1 条生效中：%s；%s",
                    records.size(),
                    active.getReasonDetail(),
                    active.getEndTime() == null ? "永久封禁" : "解封时间 " + active.getEndTime());
        } else {
            summary = String.format("共有 %d 条历史处罚记录，当前没有生效中的处罚", records.size());
        }
        return ToolResult.ok(list, summary);
    }
}
