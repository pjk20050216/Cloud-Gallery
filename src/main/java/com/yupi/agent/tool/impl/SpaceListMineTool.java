package com.yupi.agent.tool.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.yupi.agent.core.AgentContext;
import com.yupi.agent.tool.AgentTool;
import com.yupi.agent.tool.ToolResult;
import com.yupi.agent.tool.ToolRiskLevel;
import com.yupi.model.entity.Space;
import com.yupi.service.SpaceService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 查询当前用户的空间列表（只读）。
 */
@Component
public class SpaceListMineTool implements AgentTool {

    @Resource
    private SpaceService spaceService;

    @Override
    public String name() {
        return "space.listMine";
    }

    @Override
    public String description() {
        return "查询当前用户拥有的空间列表，包含空间名称、级别、图片数量与容量占用、剩余 token 额度。"
                + "当用户问“我有哪些空间”“我的额度还剩多少”时调用。";
    }

    @Override
    public Map<String, Object> parameters() {
        return JSONUtil.createObj()
                .set("type", "object")
                .set("properties", JSONUtil.createObj())
                .set("required", JSONUtil.createArray());
    }

    @Override
    public ToolRiskLevel risk() {
        return ToolRiskLevel.READ_ONLY;
    }

    @Override
    public ToolResult execute(AgentContext context, Map<String, Object> args) {
        List<Space> spaces = spaceService.lambdaQuery()
                .eq(Space::getUserId, context.getUserId())
                .orderByDesc(Space::getCreateTime)
                .list();

        if (spaces.isEmpty()) {
            return ToolResult.ok(new ArrayList<>(), "当前用户还没有创建任何空间");
        }

        List<Map<String, Object>> list = new ArrayList<>();
        for (Space space : spaces) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("spaceId", space.getId());
            item.put("spaceName", StrUtil.blankToDefault(space.getSpaceName(), "未命名空间"));
            item.put("spaceLevel", space.getSpaceLevel());
            item.put("spaceType", space.getSpaceType() == null ? 0 : space.getSpaceType());
            item.put("totalCount", space.getTotalCount());
            item.put("maxCount", space.getMaxCount());
            item.put("totalSize", space.getTotalSize());
            item.put("maxSize", space.getMaxSize());
            item.put("remainingTokens", space.getRemainingTokens());
            list.add(item);
        }

        long totalCount = spaces.stream().mapToLong(s -> s.getTotalCount() == null ? 0 : s.getTotalCount()).sum();
        long remainingTokens = spaces.stream()
                .mapToLong(s -> s.getRemainingTokens() == null ? 0 : s.getRemainingTokens()).sum();
        String summary = String.format("共 %d 个空间，累计 %d 张图片，剩余 token 合计 %d",
                spaces.size(), totalCount, remainingTokens);
        return ToolResult.ok(list, summary);
    }
}
