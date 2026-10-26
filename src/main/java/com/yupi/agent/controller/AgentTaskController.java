package com.yupi.agent.controller;

import com.yupi.agent.core.AgentContext;
import com.yupi.agent.core.AgentContextFactory;
import com.yupi.agent.model.dto.ChatRequestDTO;
import com.yupi.agent.model.vo.AgentTaskVO;
import com.yupi.agent.task.AgentTaskService;
import com.yupi.agent.task.AgentTaskTypeEnum;
import com.yupi.agent.task.AgentTaskWorker;
import com.yupi.common.BaseResponse;
import com.yupi.common.ResultUtils;
import com.yupi.exception.ErrorCode;
import com.yupi.exception.ThrowUtils;
import com.yupi.model.entity.AgentTask;
import com.yupi.model.entity.User;
import com.yupi.service.UserService;
import cn.hutool.core.util.StrUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 异步智能体任务接口。
 */
@RestController
@RequestMapping("/agent")
public class AgentTaskController {

    @Resource
    private UserService userService;

    @Resource
    private AgentContextFactory agentContextFactory;

    @Resource
    private AgentTaskService agentTaskService;

    /**
     * 提交异步对话：立即返回 taskId，后台跑完整的 ReAct 循环
     */
    @PostMapping("/chat/async")
    public BaseResponse<AgentTaskVO> chatAsync(@RequestBody ChatRequestDTO requestDTO, HttpServletRequest request) {
        ThrowUtils.throwIf(requestDTO == null || StrUtil.isBlank(requestDTO.getMessage()),
                ErrorCode.PARAMS_ERROR, "消息内容不能为空");

        User loginUser = userService.getLoginUser(request);
        // 身份快照必须在请求线程完成
        AgentContext context = agentContextFactory.build(loginUser);

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("sessionId", requestDTO.getSessionId());
        input.put("message", requestDTO.getMessage());
        input.put("agentType", requestDTO.getAgentType());

        AgentTask task = agentTaskService.submit(context.getUserId(), requestDTO.getSessionId(),
                AgentTaskTypeEnum.CHAT, AgentTaskWorker.withContext(context, input));
        return ResultUtils.success(toVO(task, false));
    }

    /**
     * 查询任务状态
     */
    @GetMapping("/task/{taskId}")
    public BaseResponse<AgentTaskVO> getTask(@PathVariable("taskId") String taskId, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        AgentTask task = agentTaskService.getByTaskId(loginUser.getId(), taskId);
        ThrowUtils.throwIf(task == null, ErrorCode.NOT_FOUND_ERROR, "任务不存在");
        return ResultUtils.success(toVO(task, true));
    }

    /**
     * 我的任务列表
     */
    @GetMapping("/task/list")
    public BaseResponse<List<AgentTaskVO>> listTasks(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        List<AgentTaskVO> list = agentTaskService.listByUser(loginUser.getId(), 20).stream()
                .map(task -> toVO(task, false))
                .collect(Collectors.toList());
        return ResultUtils.success(list);
    }

    /**
     * 取消任务
     */
    @PostMapping("/task/{taskId}/cancel")
    public BaseResponse<Boolean> cancelTask(@PathVariable("taskId") String taskId, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        boolean canceled = agentTaskService.cancel(loginUser.getId(), taskId);
        ThrowUtils.throwIf(!canceled, ErrorCode.OPERATION_ERROR, "任务不存在或已结束，无法取消");
        return ResultUtils.success(true);
    }

    // ---------------------------------------------------------------- 内部

    private AgentTaskVO toVO(AgentTask task, boolean withOutput) {
        return AgentTaskVO.builder()
                .taskId(task.getTaskId())
                .sessionId(task.getSessionId())
                .type(task.getType())
                .status(task.getStatus())
                .progress(task.getProgress())
                .progressMessage(task.getProgressMessage())
                .output(withOutput ? task.getOutputJson() : null)
                .errorMsg(task.getErrorMsg())
                .createTime(task.getCreateTime())
                .startTime(task.getStartTime())
                .endTime(task.getEndTime())
                .build();
    }
}
