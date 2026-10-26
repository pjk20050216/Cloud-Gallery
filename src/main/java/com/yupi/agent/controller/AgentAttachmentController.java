package com.yupi.agent.controller;

import com.yupi.agent.attachment.AgentAttachmentService;
import com.yupi.agent.model.vo.AttachmentVO;
import com.yupi.common.BaseResponse;
import com.yupi.common.ResultUtils;
import com.yupi.model.entity.AgentAttachment;
import com.yupi.model.entity.User;
import com.yupi.service.UserService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * 智能体附件上传。
 * <p>
 * 单独一个接口而不是塞进 SSE 请求里：
 * 1. SSE 请求体保持纯 JSON，前后端都简单；
 * 2. 附件可跨轮复用（"把刚才那张图再传一次"）。
 */
@RestController
@RequestMapping("/agent/attachment")
public class AgentAttachmentController {

    @Resource
    private UserService userService;

    @Resource
    private AgentAttachmentService agentAttachmentService;

    @PostMapping("/upload")
    public BaseResponse<AttachmentVO> upload(@RequestParam("file") MultipartFile file,
                                             HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        AgentAttachment attachment = agentAttachmentService.upload(loginUser.getId(), file);
        return ResultUtils.success(AttachmentVO.builder()
                .attachmentId(attachment.getId())
                .url(attachment.getUrl())
                .fileName(attachment.getFileName())
                .fileSize(attachment.getFileSize())
                .build());
    }
}
