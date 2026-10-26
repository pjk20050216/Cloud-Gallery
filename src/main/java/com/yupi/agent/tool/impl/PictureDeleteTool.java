package com.yupi.agent.tool.impl;

import com.yupi.agent.core.AgentContext;
import com.yupi.agent.tool.AbstractAgentTool;
import com.yupi.agent.tool.ToolResult;
import com.yupi.agent.tool.ToolRiskLevel;
import com.yupi.exception.BusinessException;
import com.yupi.manager.auth.model.SpaceUserPermissionConstant;
import com.yupi.model.entity.Picture;
import com.yupi.model.entity.User;
import com.yupi.service.PictureService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 删除图片（高危，需二次确认）。
 */
@Component
public class PictureDeleteTool extends AbstractAgentTool {

    @Resource
    private PictureService pictureService;

    @Override
    public String name() {
        return "picture.delete";
    }

    @Override
    public String description() {
        return "删除一张图片。这是不可恢复的操作，调用前必须确认用户确实要删除这张图。"
                + "当用户明确说“删掉这张图/把 X 删了”时调用。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> properties = properties();
        properties.put("pictureId", prop("integer", "要删除的图片 id"));
        return schema(properties, "pictureId");
    }

    @Override
    public ToolRiskLevel risk() {
        return ToolRiskLevel.DANGEROUS;
    }

    @Override
    public String requiredPermission() {
        return SpaceUserPermissionConstant.PICTURE_DELETE;
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
        if (!PictureEditTool.canOperate(context, picture)) {
            return ToolResult.fail("只能删除自己上传的图片");
        }

        try {
            User user = toUser(context);
            pictureService.deletePicture(user, pictureId);
        } catch (BusinessException e) {
            return ToolResult.fail(e.getMessage());
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pictureId", pictureId);
        data.put("name", picture.getName());
        return ToolResult.ok(data, "图片已删除：" + picture.getName());
    }

    /**
     * 供编排器生成确认文案
     */
    public String preview(AgentContext context, Map<String, Object> args) {
        Long pictureId = asLong(args.get("pictureId"));
        Picture picture = pictureId == null ? null : pictureService.getById(pictureId);
        String name = picture == null ? String.valueOf(pictureId) : picture.getName();
        return "即将删除图片「" + name + "」（ID=" + pictureId + "），该操作不可恢复，确认继续吗？";
    }
}
