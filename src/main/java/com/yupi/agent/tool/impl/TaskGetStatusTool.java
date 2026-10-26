package com.yupi.agent.tool.impl;

import com.yupi.agent.core.AgentContext;
import com.yupi.agent.task.AgentTaskService;
import com.yupi.agent.tool.AbstractAgentTool;
import com.yupi.agent.tool.ToolNames;
import com.yupi.agent.tool.ToolResult;
import com.yupi.agent.tool.ToolRiskLevel;
import com.yupi.model.entity.AgentTask;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 查询异步任务进度。
 */
@Component
public class TaskGetStatusTool extends AbstractAgentTool {

    @Resource
    private AgentTaskService agentTaskService;

    @Override
    public String name() {
        return ToolNames.TASK_GET_STATUS;
    }

    @Override
    public String description() {
        return "查询一个异步任务的执行状态与进度。当用户问“刚才那个任务好了吗”“进度怎么样了”，"
                + "或者你想确认批量任务是否完成时调用。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> properties = properties();
        properties.put("taskId", prop("string", "任务号，由批量操作返回"));
        return schema(properties, "taskId");
    }

    @Override
    public ToolRiskLevel risk() {
        return ToolRiskLevel.READ_ONLY;
    }

    @Override
    public ToolResult execute(AgentContext context, Map<String, Object> args) {
        String taskId = trimToNull(args.get("taskId"));
        if (taskId == null) {
            return ToolResult.fail("任务号不能为空");
        }
        AgentTask task = agentTaskService.getByTaskId(context.getUserId(), taskId);
        if (task == null) {
            return ToolResult.fail("任务不存在或不属于当前用户");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskId", task.getTaskId());
        data.put("type", task.getType());
        data.put("status", task.getStatus());
        data.put("progress", task.getProgress());
        data.put("progressMessage", task.getProgressMessage());
        if (task.getEndTime() != null) {
            data.put("endTime", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(task.getEndTime()));
        }
        if (task.getErrorMsg() != null) {
            data.put("errorMsg", task.getErrorMsg());
        }

        String summary = String.format("任务 %s：%s（%d%%）%s",
                task.getTaskId(), task.getStatus(), task.getProgress() == null ? 0 : task.getProgress(),
                task.getProgressMessage() == null ? "" : "，" + task.getProgressMessage());
        return ToolResult.ok(data, summary);
    }
}
