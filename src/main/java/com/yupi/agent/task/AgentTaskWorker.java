package com.yupi.agent.task;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yupi.agent.core.AgentContext;
import com.yupi.agent.core.AgentOrchestrator;
import com.yupi.agent.model.dto.ChatRequestDTO;
import com.yupi.agent.model.vo.ChatResponseVO;
import com.yupi.mapper.AgentTaskMapper;
import com.yupi.model.dto.picture.PictureUploadByBatchRequest;
import com.yupi.model.entity.AgentTask;
import com.yupi.model.entity.User;
import com.yupi.service.PictureService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 异步任务执行器。
 * <p>
 * 注意：这里运行在 agentExecutor 线程里，
 * 没有 HttpServletRequest、没有 Sa-Token 上下文，所有身份信息来自任务里存的快照。
 */
@Slf4j
@Component
public class AgentTaskWorker {

    @Resource
    private AgentTaskMapper agentTaskMapper;

    @Resource
    private AgentTaskService agentTaskService;

    @Resource
    private AgentOrchestrator agentOrchestrator;

    @Resource
    private PictureService pictureService;

    @Resource
    private ObjectMapper objectMapper;

    public void run(String taskId) {
        AgentTask task = agentTaskMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AgentTask>()
                        .eq(AgentTask::getTaskId, taskId));
        if (task == null) {
            log.warn("任务不存在：{}", taskId);
            return;
        }
        if (!AgentTaskStatusEnum.PENDING.name().equals(task.getStatus())) {
            return;
        }

        agentTaskService.markRunning(taskId);
        try {
            Object output;
            if (AgentTaskTypeEnum.CHAT.name().equals(task.getType())) {
                output = runChat(task);
            } else if (AgentTaskTypeEnum.BATCH_UPLOAD.name().equals(task.getType())) {
                output = runBatchUpload(task);
            } else {
                throw new IllegalStateException("未知任务类型：" + task.getType());
            }

            // 执行期间用户可能已经取消，不要覆盖取消状态
            if (agentTaskService.isCanceled(taskId)) {
                log.info("任务 {} 已被取消，丢弃结果", taskId);
                return;
            }
            agentTaskService.markSuccess(taskId, output);
        } catch (Exception e) {
            log.error("任务 {} 执行失败", taskId, e);
            if (!agentTaskService.isCanceled(taskId)) {
                agentTaskService.markFailed(taskId, e.getMessage() == null
                        ? e.getClass().getSimpleName() : e.getMessage());
            }
        }
    }

    // ---------------------------------------------------------------- 对话任务

    private Object runChat(AgentTask task) throws Exception {
        ChatRequestDTO request = objectMapper.readValue(task.getInputJson(), ChatRequestDTO.class);
        AgentContext context = readContext(task);

        agentTaskService.updateProgress(task.getTaskId(), 10, "正在思考");

        ChatResponseVO response = agentOrchestrator.run(context, request, null, null);

        agentTaskService.updateProgress(task.getTaskId(), 90, "整理结果");

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("sessionId", response.getSessionId());
        output.put("messageId", response.getMessageId());
        output.put("agentType", response.getAgentType());
        output.put("content", response.getContent());
        output.put("toolCalls", response.getToolCalls());
        output.put("promptTokens", response.getPromptTokens());
        output.put("completionTokens", response.getCompletionTokens());
        output.put("pendingConfirm", response.getPendingConfirm());
        return output;
    }

    // ---------------------------------------------------------------- 批量上传任务

    private Object runBatchUpload(AgentTask task) throws Exception {
        Map<?, ?> input = objectMapper.readValue(task.getInputJson(), Map.class);
        AgentContext context = readContext(task);

        PictureUploadByBatchRequest request = new PictureUploadByBatchRequest();
        request.setSearchText(asString(input.get("searchText")));
        request.setNamePrefix(asString(input.get("namePrefix")));
        request.setCount(asInt(input.get("count"), agentTaskService.batchUploadMaxCount()));

        User user = User.builder()
                .id(context.getUserId())
                .userAccount(context.getUserAccount())
                .userName(context.getUserName())
                .userRole(context.isAdmin() ? "admin" : "user")
                .build();

        agentTaskService.updateProgress(task.getTaskId(), 5, "正在抓取图片列表");

        Integer uploaded = pictureService.uploadPictureByBatch(request, user, done -> {
            if (agentTaskService.isCanceled(task.getTaskId())) {
                throw new IllegalStateException("任务已取消");
            }
            int progress = 10 + (int) (done * 80.0 / Math.max(1, request.getCount()));
            agentTaskService.updateProgress(task.getTaskId(), Math.min(progress, 90),
                    "已上传 " + done + " 张");
        });

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("uploaded", uploaded);
        output.put("searchText", request.getSearchText());
        return output;
    }

    // ---------------------------------------------------------------- 工具

    /**
     * 任务里存的身份快照
     */
    private AgentContext readContext(AgentTask task) throws Exception {
        Map<?, ?> input = objectMapper.readValue(task.getInputJson(), Map.class);
        Object raw = input.get("__context");
        if (raw == null) {
            throw new IllegalStateException("任务缺少身份快照");
        }
        return objectMapper.convertValue(raw, AgentContext.class);
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private int asInt(Object value, int defaultValue) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 给提交方用的：把身份快照塞进输入里
     */
    public static Map<String, Object> withContext(AgentContext context, Map<String, Object> input) {
        Map<String, Object> payload = new LinkedHashMap<>(input);
        payload.put("__context", context);
        return payload;
    }

    /**
     * 判断任务是否超时
     */
    public boolean isTimeout(AgentTask task, int timeoutSeconds) {
        if (task.getStartTime() == null) {
            return false;
        }
        long elapsed = (System.currentTimeMillis() - task.getStartTime().getTime()) / 1000;
        return StrUtil.isNotBlank(String.valueOf(elapsed)) && elapsed > timeoutSeconds;
    }
}
