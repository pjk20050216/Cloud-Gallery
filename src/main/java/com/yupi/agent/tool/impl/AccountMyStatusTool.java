package com.yupi.agent.tool.impl;

import com.yupi.agent.core.AgentContext;
import com.yupi.agent.tool.AbstractAgentTool;
import com.yupi.agent.tool.ToolNames;
import com.yupi.agent.tool.ToolResult;
import com.yupi.agent.tool.ToolRiskLevel;
import com.yupi.model.entity.Space;
import com.yupi.model.entity.User;
import com.yupi.service.SpaceService;
import com.yupi.service.UserService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 查询当前账号的状态与额度。
 * <p>
 * 说明：封禁体系（user_ban_record）在 P4 落地。当前如实返回"未启用封禁功能"，
 * 绝不编造处罚记录。
 */
@Component
public class AccountMyStatusTool extends AbstractAgentTool {

    @Resource
    private UserService userService;

    @Resource
    private SpaceService spaceService;

    @Override
    public String name() {
        return ToolNames.ACCOUNT_MY_STATUS;
    }

    @Override
    public String description() {
        return "查询当前登录账号的状态：账号、昵称、角色、注册时间、空间数量、剩余 token 额度，"
                + "以及是否存在封禁记录。当用户问“我账号怎么了”“我为什么被封”“我还剩多少额度”时调用。";
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
        User user = userService.getById(context.getUserId());
        if (user == null) {
            return ToolResult.fail("账号不存在");
        }

        List<Space> spaces = spaceService.lambdaQuery()
                .eq(Space::getUserId, context.getUserId())
                .list();
        long remainingTokens = spaces.stream()
                .mapToLong(space -> space.getRemainingTokens() == null ? 0 : space.getRemainingTokens())
                .sum();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userAccount", user.getUserAccount());
        data.put("userName", user.getUserName());
        data.put("userRole", user.getUserRole());
        if (user.getCreateTime() != null) {
            data.put("createTime", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(user.getCreateTime()));
        }
        data.put("spaceCount", spaces.size());
        data.put("totalRemainingTokens", remainingTokens);

        // 封禁状态来自 AgentContext 快照（请求线程已查好）
        data.put("banned", context.isBanned());
        if (context.isBanned()) {
            data.put("banMessage", context.getBanMessage());
        }

        String summary;
        if (context.isBanned()) {
            summary = String.format("账号 %s 处于封禁状态。%s", user.getUserAccount(), context.getBanMessage());
        } else {
            summary = String.format("账号 %s（%s），%d 个空间，剩余 token %d，无生效中的处罚",
                    user.getUserAccount(),
                    "admin".equals(user.getUserRole()) ? "管理员" : "普通用户",
                    spaces.size(), remainingTokens);
        }
        return ToolResult.ok(data, summary);
    }
}
