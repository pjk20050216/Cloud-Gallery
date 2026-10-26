package com.yupi.agent.controller;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yupi.agent.config.AgentProperties;
import com.yupi.agent.core.AgentContext;
import com.yupi.agent.core.AgentContextFactory;
import com.yupi.agent.core.AgentException;
import com.yupi.agent.core.AgentOrchestrator;
import com.yupi.agent.model.dto.ChatRequestDTO;
import com.yupi.agent.model.dto.ConfirmRequestDTO;
import com.yupi.agent.model.vo.ChatEventVO;
import com.yupi.agent.model.vo.ChatResponseVO;
import com.yupi.common.BaseResponse;
import com.yupi.common.ResultUtils;
import com.yupi.exception.BusinessException;
import com.yupi.exception.ErrorCode;
import com.yupi.exception.ThrowUtils;
import com.yupi.model.entity.User;
import com.yupi.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.concurrent.Executor;

/**
 * 智能体对话接口。
 * <p>
 * 注意：这里**故意不写** {@code produces = text/event-stream}。
 * 否则未登录等异常经 GlobalExceptionHandler 返回 JSON 时会因内容协商失败变成 406/500，
 * 拿不到友好的错误码。SseEmitter 返回类型本身会设置正确的 Content-Type。
 */
@Slf4j
@RestController
@RequestMapping("/agent")
public class AgentChatController {

    private static final int MAX_MESSAGE_LENGTH = 2000;

    @Resource
    private UserService userService;

    @Resource
    private AgentContextFactory agentContextFactory;

    @Resource
    private AgentOrchestrator agentOrchestrator;

    @Resource
    private AgentProperties agentProperties;

    @Resource
    private ObjectMapper objectMapper;

    @Resource(name = "agentSseExecutor")
    private Executor agentSseExecutor;

    /**
     * 非流式对话（调试 / 降级用）
     */
    @PostMapping("/chat")
    public BaseResponse<ChatResponseVO> chat(@RequestBody ChatRequestDTO requestDTO, HttpServletRequest request) {
        validate(requestDTO);
        User loginUser = userService.getLoginUser(request);
        AgentContext context = agentContextFactory.build(loginUser);
        ChatResponseVO response = agentOrchestrator.run(context, requestDTO, null, null);
        return ResultUtils.success(response);
    }

    /**
     * 流式对话（SSE）
     */
    @PostMapping("/chat/stream")
    public SseEmitter chatStream(@RequestBody ChatRequestDTO requestDTO, HttpServletRequest request) {
        validate(requestDTO);
        // 身份快照必须在请求线程完成
        User loginUser = userService.getLoginUser(request);
        AgentContext context = agentContextFactory.build(loginUser);

        SseEmitter emitter = newSseEmitter(requestDTO.getSessionId());
        agentSseExecutor.execute(() -> {
            try {
                agentOrchestrator.run(context, requestDTO,
                        event -> send(emitter, event),
                        delta -> send(emitter, ChatEventVO.builder().type("delta").content(delta).build()));
                emitter.complete();
            } catch (AgentException e) {
                log.debug("客户端断开，停止推送：{}", e.getMessage());
                emitter.complete();
            } catch (BusinessException e) {
                safeSend(emitter, errorEvent(e.getCode(), e.getMessage()));
                emitter.complete();
            } catch (Exception e) {
                log.error("智能体流式执行失败", e);
                safeSend(emitter, errorEvent(ErrorCode.SYSTEM_ERROR.getCode(), "AI 服务暂时不可用，请稍后重试"));
                emitter.complete();
            }
        });
        return emitter;
    }

    /**
     * 高危操作确认（非流式）
     */
    @PostMapping("/confirm")
    public BaseResponse<ChatResponseVO> confirm(@RequestBody ConfirmRequestDTO requestDTO, HttpServletRequest request) {
        ThrowUtils.throwIf(requestDTO == null || StrUtil.isBlank(requestDTO.getConfirmToken()),
                ErrorCode.PARAMS_ERROR, "确认令牌不能为空");
        User loginUser = userService.getLoginUser(request);
        AgentContext context = agentContextFactory.build(loginUser);
        ChatResponseVO response = agentOrchestrator.resume(
                context, requestDTO.getConfirmToken(),
                !Boolean.FALSE.equals(requestDTO.getApprove()), null, null);
        return ResultUtils.success(response);
    }

    /**
     * 高危操作确认（SSE，与 /chat/stream 事件格式一致）
     */
    @PostMapping("/confirm/stream")
    public SseEmitter confirmStream(@RequestBody ConfirmRequestDTO requestDTO, HttpServletRequest request) {
        ThrowUtils.throwIf(requestDTO == null || StrUtil.isBlank(requestDTO.getConfirmToken()),
                ErrorCode.PARAMS_ERROR, "确认令牌不能为空");
        User loginUser = userService.getLoginUser(request);
        AgentContext context = agentContextFactory.build(loginUser);

        boolean approved = !Boolean.FALSE.equals(requestDTO.getApprove());
        SseEmitter emitter = newSseEmitter(null);
        agentSseExecutor.execute(() -> {
            try {
                agentOrchestrator.resume(context, requestDTO.getConfirmToken(), approved,
                        event -> send(emitter, event),
                        delta -> send(emitter, ChatEventVO.builder().type("delta").content(delta).build()));
                emitter.complete();
            } catch (AgentException e) {
                log.debug("客户端断开，停止推送：{}", e.getMessage());
                emitter.complete();
            } catch (BusinessException e) {
                safeSend(emitter, errorEvent(e.getCode(), e.getMessage()));
                emitter.complete();
            } catch (Exception e) {
                log.error("智能体确认流程执行失败", e);
                safeSend(emitter, errorEvent(ErrorCode.SYSTEM_ERROR.getCode(), "AI 服务暂时不可用，请稍后重试"));
                emitter.complete();
            }
        });
        return emitter;
    }

    // ---------------------------------------------------------------- 内部

    private SseEmitter newSseEmitter(Long sessionId) {
        SseEmitter emitter = new SseEmitter(agentProperties.getChat().getSseTimeoutMs());
        emitter.onTimeout(() -> {
            log.warn("智能体 SSE 超时，sessionId={}", sessionId);
            emitter.complete();
        });
        emitter.onError(e -> log.warn("智能体 SSE 异常：{}", e.getMessage()));
        return emitter;
    }

    private void validate(ChatRequestDTO requestDTO) {
        ThrowUtils.throwIf(requestDTO == null, ErrorCode.PARAMS_ERROR, "请求参数为空");
        ThrowUtils.throwIf(StrUtil.isBlank(requestDTO.getMessage()), ErrorCode.PARAMS_ERROR, "消息内容不能为空");
        ThrowUtils.throwIf(requestDTO.getMessage().length() > MAX_MESSAGE_LENGTH,
                ErrorCode.PARAMS_ERROR, "消息内容过长，最多 " + MAX_MESSAGE_LENGTH + " 字");
        ThrowUtils.throwIf(!agentProperties.isEnabled(), ErrorCode.FORBIDDEN_ERROR, "智能体功能暂未开启");
    }

    private ChatEventVO errorEvent(int code, String message) {
        return ChatEventVO.builder().type("error").code(code).message(message).build();
    }

    private void send(SseEmitter emitter, ChatEventVO event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            emitter.send(SseEmitter.event()
                    .name(StrUtil.blankToDefault(event.getType(), "message"))
                    .data(payload, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            // 客户端断开 / emitter 已完成：抛出以中断推理循环
            throw new AgentException("SSE 推送失败", e);
        }
    }

    private void safeSend(SseEmitter emitter, ChatEventVO event) {
        try {
            send(emitter, event);
        } catch (Exception ignore) {
            log.debug("错误事件推送失败：{}", ignore.getMessage());
        }
    }
}
