package com.yupi.agent.controller;

import com.yupi.agent.model.vo.MessageVO;
import com.yupi.agent.model.vo.SessionVO;
import com.yupi.agent.session.AgentSessionStore;
import com.yupi.common.BaseResponse;
import com.yupi.common.ResultUtils;
import com.yupi.exception.ErrorCode;
import com.yupi.exception.ThrowUtils;
import com.yupi.model.entity.AgentMessage;
import com.yupi.model.entity.AgentSession;
import com.yupi.model.entity.User;
import com.yupi.service.UserService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 智能体会话管理接口
 */
@RestController
@RequestMapping("/agent/session")
public class AgentSessionController {

    @Resource
    private UserService userService;

    @Resource
    private AgentSessionStore sessionStore;

    /**
     * 会话列表
     */
    @GetMapping("/list")
    public BaseResponse<List<SessionVO>> listSessions(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        List<AgentSession> sessions = sessionStore.listSessions(loginUser.getId(), 50);
        List<SessionVO> list = sessions.stream()
                .map(session -> SessionVO.builder()
                        .id(session.getId())
                        .title(session.getTitle())
                        .agentType(session.getAgentType())
                        .messageCount(session.getMessageCount())
                        .lastMessageTime(session.getLastMessageTime())
                        .createTime(session.getCreateTime())
                        .build())
                .collect(Collectors.toList());
        return ResultUtils.success(list);
    }

    /**
     * 历史消息
     */
    @GetMapping("/{id}/messages")
    public BaseResponse<List<MessageVO>> listMessages(@PathVariable("id") Long id,
                                                      @RequestParam(value = "current", defaultValue = "1") long current,
                                                      @RequestParam(value = "pageSize", defaultValue = "20") long pageSize,
                                                      HttpServletRequest request) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        AgentSession session = sessionStore.getOwned(loginUser.getId(), id);
        ThrowUtils.throwIf(session == null, ErrorCode.NOT_FOUND_ERROR, "会话不存在");

        List<AgentMessage> messages = sessionStore.listMessages(id, current, pageSize);
        List<MessageVO> list = messages.stream()
                .map(message -> MessageVO.builder()
                        .id(message.getId())
                        .role(message.getRole())
                        .agentType(message.getAgentType())
                        .content(message.getContent())
                        .toolName(message.getToolName())
                        .status(message.getStatus())
                        .createTime(message.getCreateTime())
                        .build())
                .collect(Collectors.toList());
        return ResultUtils.success(list);
    }

    /**
     * 删除会话（逻辑删除，连同其消息）
     */
    @DeleteMapping("/{id}")
    public BaseResponse<Boolean> deleteSession(@PathVariable("id") Long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        boolean deleted = sessionStore.deleteSession(loginUser.getId(), id);
        ThrowUtils.throwIf(!deleted, ErrorCode.NOT_FOUND_ERROR, "会话不存在");
        return ResultUtils.success(true);
    }
}
