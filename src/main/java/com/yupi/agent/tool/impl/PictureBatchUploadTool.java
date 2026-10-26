package com.yupi.agent.tool.impl;

import com.yupi.agent.core.AgentContext;
import com.yupi.agent.task.AgentTaskService;
import com.yupi.agent.task.AgentTaskTypeEnum;
import com.yupi.agent.task.AgentTaskWorker;
import com.yupi.agent.tool.AbstractAgentTool;
import com.yupi.agent.tool.ToolNames;
import com.yupi.agent.tool.ToolResult;
import com.yupi.agent.tool.ToolRiskLevel;
import com.yupi.manager.auth.model.SpaceUserPermissionConstant;
import com.yupi.model.entity.AgentTask;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 批量抓取并上传图片（长耗时工具）。
 * <p>
 * 不阻塞对话：立即创建异步任务并返回 taskId，由前端或 task.getStatus 轮询。
 */
@Component
public class PictureBatchUploadTool extends AbstractAgentTool {

    @Resource
    private AgentTaskService agentTaskService;

    @Override
    public String name() {
        return ToolNames.PICTURE_BATCH_UPLOAD;
    }

    @Override
    public String description() {
        return "按关键词从网上批量抓取图片并上传到图库。这是耗时操作，会立即返回一个任务号，"
                + "之后可以用 task.getStatus 查询进度。当用户说“帮我批量下载/抓取 N 张 X 的图片”时调用。"
                + "仅管理员可用，单次最多 30 张。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> properties = properties();
        properties.put("searchText", prop("string", "搜索关键词，例如“猫咪”"));
        properties.put("count", prop("integer", "抓取数量，1~30，默认 10"));
        properties.put("namePrefix", prop("string", "图片名称前缀；不传则用关键词"));
        return schema(properties, "searchText");
    }

    @Override
    public ToolRiskLevel risk() {
        return ToolRiskLevel.WRITE;
    }

    @Override
    public String requiredPermission() {
        return SpaceUserPermissionConstant.PICTURE_UPLOAD;
    }

    @Override
    public ToolResult execute(AgentContext context, Map<String, Object> args) {
        if (!context.isAdmin()) {
            return ToolResult.fail("批量抓取仅管理员可用");
        }
        String searchText = trimToNull(args.get("searchText"));
        if (searchText == null) {
            return ToolResult.fail("搜索关键词不能为空");
        }

        int max = agentTaskService.batchUploadMaxCount();
        int count = (int) clamp(asLong(args.get("count")), 1, max, 10);

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("searchText", searchText);
        input.put("count", count);
        input.put("namePrefix", trimToNull(args.get("namePrefix")));

        AgentTask task = agentTaskService.submit(context.getUserId(), null,
                AgentTaskTypeEnum.BATCH_UPLOAD, AgentTaskWorker.withContext(context, input));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskId", task.getTaskId());
        data.put("status", task.getStatus());
        data.put("count", count);
        return ToolResult.ok(data, "已提交批量抓取任务，任务号 " + task.getTaskId() + "，共 " + count + " 张");
    }
}
