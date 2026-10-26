package com.yupi.agent.tool.impl;

import cn.hutool.core.io.FileUtil;
import com.yupi.agent.attachment.AgentAttachmentService;
import com.yupi.agent.attachment.FileMultipartFile;
import com.yupi.agent.core.AgentContext;
import com.yupi.agent.tool.AbstractAgentTool;
import com.yupi.agent.tool.ToolResult;
import com.yupi.agent.tool.ToolRiskLevel;
import com.yupi.exception.BusinessException;
import com.yupi.manager.auth.model.SpaceUserPermissionConstant;
import com.yupi.model.dto.picture.PictureUploadRequest;
import com.yupi.model.entity.AgentAttachment;
import com.yupi.model.entity.User;
import com.yupi.model.vo.PictureVO;
import com.yupi.service.PictureService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;
import java.util.Map;

/**
 * 把用户在对话中上传的图片附件保存进图库。
 */
@Slf4j
@Component
public class PictureUploadFromAttachmentTool extends AbstractAgentTool {

    @Resource
    private PictureService pictureService;

    @Resource
    private AgentAttachmentService agentAttachmentService;

    @Override
    public String name() {
        return "picture.uploadFromAttachment";
    }

    @Override
    public String description() {
        return "把用户在本次对话中上传的图片保存到图库。"
                + "当用户上传了图片并说“把这张图传到图库/保存这张图/存起来”时调用。"
                + "attachmentId 取自用户消息中携带的附件 id。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> properties = properties();
        properties.put("attachmentId", prop("integer", "用户上传的附件 id"));
        properties.put("picName", prop("string", "图片名称；用户没说就留空"));
        properties.put("spaceId", prop("integer", "空间 id；不传表示公共图库"));
        return schema(properties, "attachmentId");
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
        Long attachmentId = asLong(args.get("attachmentId"));
        if (attachmentId == null) {
            return ToolResult.fail("附件 id 不能为空");
        }
        AgentAttachment attachment = agentAttachmentService.getOwned(context.getUserId(), attachmentId);
        if (attachment == null) {
            return ToolResult.fail("附件不存在或不属于当前用户");
        }

        Long spaceId = asLong(args.get("spaceId"));
        if (spaceId != null && !context.canAccessSpace(spaceId)) {
            return ToolResult.fail("无权向该空间上传图片");
        }

        File tempFile = null;
        try {
            tempFile = agentAttachmentService.downloadToTemp(attachment);
            FileMultipartFile multipartFile = new FileMultipartFile(
                    tempFile, attachment.getFileName(), attachment.getContentType());

            PictureUploadRequest request = new PictureUploadRequest();
            request.setPicName(trimToNull(args.get("picName")));
            request.setSpaceId(spaceId);

            User user = toUser(context);
            PictureVO pictureVO = pictureService.uploadPicture(multipartFile, request, user);

            agentAttachmentService.markConsumed(attachmentId);
            agentAttachmentService.deleteCosObject(attachment);

            return ToolResult.ok(PictureUploadByUrlTool.brief(pictureVO),
                    String.format("已保存到图库：图片「%s」，ID=%s", pictureVO.getName(), pictureVO.getId()));
        } catch (BusinessException e) {
            return ToolResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("附件入图库失败，attachmentId={}", attachmentId, e);
            return ToolResult.fail("保存失败，请稍后重试");
        } finally {
            if (tempFile != null) {
                FileUtil.del(tempFile);
            }
        }
    }
}
