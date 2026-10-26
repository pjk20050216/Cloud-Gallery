package com.yupi.agent.tool.impl;

import com.yupi.agent.core.AgentContext;
import com.yupi.agent.tool.AbstractAgentTool;
import com.yupi.agent.tool.ToolResult;
import com.yupi.agent.tool.ToolRiskLevel;
import com.yupi.manager.auth.model.SpaceUserPermissionConstant;
import com.yupi.model.entity.Picture;
import com.yupi.service.PictureService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 查询单张图片详情（只读）。
 */
@Component
public class PictureGetDetailTool extends AbstractAgentTool {

    @Resource
    private PictureService pictureService;

    @Override
    public String name() {
        return "picture.getDetail";
    }

    @Override
    public String description() {
        return "根据图片 id 查询一张图片的详细信息（名称、简介、分类、标签、审核状态等）。"
                + "当用户提到某个图片 id，或需要确认图片信息再决定下一步操作时调用。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> properties = properties();
        properties.put("pictureId", prop("integer", "图片 id"));
        return schema(properties, "pictureId");
    }

    @Override
    public ToolRiskLevel risk() {
        return ToolRiskLevel.READ_ONLY;
    }

    @Override
    public String requiredPermission() {
        return SpaceUserPermissionConstant.PICTURE_VIEW;
    }

    @Override
    public ToolResult execute(AgentContext context, Map<String, Object> args) {
        Long pictureId = asLong(args.get("pictureId"));
        if (pictureId == null) {
            return ToolResult.fail("图片 id 不能为空");
        }
        Picture picture = pictureService.getById(pictureId);
        if (picture == null) {
            return ToolResult.fail("图片不存在");
        }
        if (!PictureEditTool.canOperate(context, picture) && !context.isAdmin()) {
            return ToolResult.fail("无权查看这张图片");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pictureId", picture.getId());
        data.put("name", picture.getName());
        data.put("introduction", picture.getIntroduction());
        data.put("category", picture.getCategory());
        data.put("tags", picture.getTags());
        data.put("url", picture.getUrl());
        data.put("thumbnailUrl", picture.getThumbnailUrl());
        data.put("spaceId", picture.getSpaceId());
        data.put("picSize", picture.getPicSize());
        data.put("picWidth", picture.getPicWidth());
        data.put("picHeight", picture.getPicHeight());
        data.put("picFormat", picture.getPicFormat());
        data.put("reviewStatus", picture.getReviewStatus());
        data.put("reviewMessage", picture.getReviewMessage());
        if (picture.getCreateTime() != null) {
            data.put("createTime", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(picture.getCreateTime()));
        }
        return ToolResult.ok(data, "图片「" + picture.getName() + "」");
    }
}
