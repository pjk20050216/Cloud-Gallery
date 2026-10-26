package com.yupi.agent.tool.impl;

import com.yupi.agent.core.AgentContext;
import com.yupi.agent.tool.AbstractAgentTool;
import com.yupi.agent.tool.ToolResult;
import com.yupi.agent.tool.ToolRiskLevel;
import com.yupi.exception.BusinessException;
import com.yupi.manager.auth.model.SpaceUserPermissionConstant;
import com.yupi.model.dto.picture.PictureUploadRequest;
import com.yupi.model.entity.User;
import com.yupi.model.vo.PictureVO;
import com.yupi.service.PictureService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 通过 URL 把图片上传到图库。
 */
@Component
public class PictureUploadByUrlTool extends AbstractAgentTool {

    @Resource
    private PictureService pictureService;

    @Override
    public String name() {
        return "picture.uploadByUrl";
    }

    @Override
    public String description() {
        return "把一张网络图片（http/https 链接）上传到当前用户的图库。"
                + "当用户给出图片链接并说“保存/上传/收藏这张图”时调用。"
                + "如果是用户在对话里上传的图片，请改用 picture.uploadFromAttachment。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> properties = properties();
        properties.put("url", prop("string", "图片的网络地址，必须以 http:// 或 https:// 开头"));
        properties.put("picName", prop("string", "图片名称；用户没说就留空，系统会用原文件名"));
        properties.put("spaceId", prop("integer", "空间 id；不传表示上传到公共图库"));
        return schema(properties, "url");
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
        String url = trimToNull(args.get("url"));
        if (url == null) {
            return ToolResult.fail("图片地址不能为空");
        }
        Long spaceId = asLong(args.get("spaceId"));
        if (spaceId != null && !context.canAccessSpace(spaceId)) {
            return ToolResult.fail("无权向该空间上传图片");
        }

        PictureUploadRequest request = new PictureUploadRequest();
        request.setUrl(url);
        request.setPicName(trimToNull(args.get("picName")));
        request.setSpaceId(spaceId);

        try {
            User user = toUser(context);
            PictureVO pictureVO = pictureService.uploadPicture(url, request, user);
            return ToolResult.ok(brief(pictureVO),
                    String.format("上传成功：图片「%s」，ID=%s", pictureVO.getName(), pictureVO.getId()));
        } catch (BusinessException e) {
            return ToolResult.fail(e.getMessage());
        }
    }

    static Map<String, Object> brief(PictureVO pictureVO) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pictureId", pictureVO.getId());
        data.put("name", pictureVO.getName());
        data.put("url", pictureVO.getUrl());
        data.put("thumbnailUrl", pictureVO.getThumbnailUrl());
        data.put("spaceId", pictureVO.getSpaceId());
        return data;
    }
}
