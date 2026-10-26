package com.yupi.agent.tool.impl;

import cn.hutool.json.JSONUtil;
import com.yupi.agent.core.AgentContext;
import com.yupi.agent.tool.AgentTool;
import com.yupi.agent.tool.ToolResult;
import com.yupi.agent.tool.ToolRiskLevel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 能力清单工具：让模型知道自己能做什么，也方便前端渲染引导语。
 */
@Component
public class HelpListCapabilitiesTool implements AgentTool {

    private static final List<Map<String, Object>> CAPABILITIES = new ArrayList<>();

    static {
        CAPABILITIES.add(capability("图片检索", "按关键词、分类、标签查找你图库里的图片"));
        CAPABILITIES.add(capability("空间查询", "查看你拥有的空间、容量占用与剩余 token 额度"));
        CAPABILITIES.add(capability("图片上传", "把网络图片或对话中上传的图片保存到图库"));
        CAPABILITIES.add(capability("图片编辑", "修改图片名称、简介、分类、标签"));
        CAPABILITIES.add(capability("图片删除", "删除图片（需要你二次确认）"));
        CAPABILITIES.add(capability("AI 扩图", "对已有图片进行智能扩图（即将开放）"));
        CAPABILITIES.add(capability("账号答疑", "解释账号状态、封禁原因与申诉入口（即将开放）"));
    }

    private static Map<String, Object> capability(String name, String description) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("description", description);
        return item;
    }

    @Override
    public String name() {
        return "help.listCapabilities";
    }

    @Override
    public String description() {
        return "列出当前智能体支持的能力。当用户问“你能做什么”“怎么用”或提出超出能力范围的请求时调用。";
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
        return ToolResult.ok(CAPABILITIES, "当前共支持 " + CAPABILITIES.size() + " 项能力");
    }
}
