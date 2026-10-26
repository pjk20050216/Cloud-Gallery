package com.yupi.agent.tool.impl;

import com.yupi.agent.core.AgentContext;
import com.yupi.agent.tool.AbstractAgentTool;
import com.yupi.agent.tool.ToolResult;
import com.yupi.agent.tool.ToolRiskLevel;
import com.yupi.exception.BusinessException;
import com.yupi.manager.auth.model.SpaceUserPermissionConstant;
import com.yupi.model.dto.picture.PictureEditRequest;
import com.yupi.model.entity.Picture;
import com.yupi.model.entity.User;
import com.yupi.service.PictureService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 编辑图片的名称 / 简介 / 分类 / 标签。
 */
@Component
public class PictureEditTool extends AbstractAgentTool {

    @Resource
    private PictureService pictureService;

    @Override
    public String name() {
        return "picture.edit";
    }

    @Override
    public String description() {
        return "修改一张已有图片的名称、简介、分类或标签。"
                + "当用户说“把这张图改名叫 X”“给它加个标签 Y”时调用。"
                + "只传需要修改的字段，未传的字段保持不变。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> properties = properties();
        properties.put("pictureId", prop("integer", "要修改的图片 id"));
        properties.put("name", prop("string", "新的图片名称"));
        properties.put("introduction", prop("string", "新的简介"));
        properties.put("category", prop("string", "新的分类"));
        properties.put("tags", arrayProp("string", "新的标签列表，会覆盖原有标签"));
        return schema(properties, "pictureId");
    }

    @Override
    public ToolRiskLevel risk() {
        return ToolRiskLevel.WRITE;
    }

    @Override
    public String requiredPermission() {
        return SpaceUserPermissionConstant.PICTURE_EDIT;
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
        if (!canOperate(context, picture)) {
            return ToolResult.fail("只能修改自己上传的图片");
        }

        PictureEditRequest request = new PictureEditRequest();
        request.setId(pictureId);
        request.setName(trimToNull(args.get("name")));
        request.setIntroduction(trimToNull(args.get("introduction")));
        request.setCategory(trimToNull(args.get("category")));
        List<String> tags = asStringList(args.get("tags"));
        if (!tags.isEmpty()) {
            request.setTags(tags);
        }

        if (request.getName() == null && request.getIntroduction() == null
                && request.getCategory() == null && request.getTags() == null) {
            return ToolResult.fail("没有需要修改的字段");
        }

        try {
            User user = toUser(context);
            pictureService.editPicture(request, user);
        } catch (BusinessException e) {
            return ToolResult.fail(e.getMessage());
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pictureId", pictureId);
        data.put("name", request.getName());
        data.put("category", request.getCategory());
        data.put("tags", request.getTags());
        return ToolResult.ok(data, "图片信息已更新");
    }

    /**
     * 归属校验：本人或管理员
     */
    static boolean canOperate(AgentContext context, Picture picture) {
        return context.isAdmin() || context.getUserId().equals(picture.getUserId());
    }
}
