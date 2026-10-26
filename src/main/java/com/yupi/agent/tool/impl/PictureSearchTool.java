package com.yupi.agent.tool.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.agent.core.AgentContext;
import com.yupi.agent.tool.AgentTool;
import com.yupi.agent.tool.ToolResult;
import com.yupi.agent.tool.ToolRiskLevel;
import com.yupi.exception.BusinessException;
import com.yupi.exception.ErrorCode;
import com.yupi.model.dto.picture.PictureQueryRequest;
import com.yupi.model.entity.Picture;
import com.yupi.service.PictureService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 图片检索工具（只读）。
 * <p>
 * 强制按当前用户过滤，模型即使传了别人的 spaceId 也查不到数据。
 */
@Component
public class PictureSearchTool implements AgentTool {

    private static final int MAX_PAGE_SIZE = 20;

    @Resource
    private PictureService pictureService;

    @Override
    public String name() {
        return "picture.search";
    }

    @Override
    public String description() {
        return "在当前用户的图库里搜索图片，支持关键词（匹配名称与简介）、分类、标签、空间。"
                + "当用户想找图片、查看某个空间的图片、或统计图片数量时调用。";
    }

    @Override
    public Map<String, Object> parameters() {
        return JSONUtil.createObj()
                .set("type", "object")
                .set("properties", JSONUtil.createObj()
                        .set("keyword", JSONUtil.createObj()
                                .set("type", "string")
                                .set("description", "关键词，匹配图片名称与简介"))
                        .set("category", JSONUtil.createObj()
                                .set("type", "string")
                                .set("description", "分类，例如 模板/电商/表情包/素材/海报"))
                        .set("tags", JSONUtil.createObj()
                                .set("type", "array")
                                .set("description", "标签列表，例如 [\"热门\",\"搞笑\"]")
                                .set("items", JSONUtil.createObj().set("type", "string")))
                        .set("spaceId", JSONUtil.createObj()
                                .set("type", "integer")
                                .set("description", "空间 id；不传表示搜索当前用户的全部图片"))
                        .set("pageSize", JSONUtil.createObj()
                                .set("type", "integer")
                                .set("description", "返回条数，1~20，默认 10")))
                .set("required", JSONUtil.createArray());
    }

    @Override
    public ToolRiskLevel risk() {
        return ToolRiskLevel.READ_ONLY;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ToolResult execute(AgentContext context, Map<String, Object> args) {
        Long spaceId = asLong(args.get("spaceId"));
        if (spaceId != null && !context.canAccessSpace(spaceId)) {
            return ToolResult.fail("无权访问该空间");
        }

        int pageSize = (int) clamp(asLong(args.get("pageSize")), 1, MAX_PAGE_SIZE, 10);

        PictureQueryRequest query = new PictureQueryRequest();
        // 关键：身份来自上下文，不接受模型传入
        query.setUserId(context.getUserId());
        query.setSpaceId(spaceId);
        query.setSearchText(StrUtil.trim(asString(args.get("keyword"))));
        query.setCategory(StrUtil.trim(asString(args.get("category"))));
        query.setTags(asStringList(args.get("tags")));
        query.setPageSize(pageSize);
        query.setCurrent(1);

        Page<Picture> page;
        try {
            page = pictureService.page(new Page<>(1, pageSize), pictureService.getQueryWrapper(query));
        } catch (BusinessException e) {
            return ToolResult.fail(e.getMessage());
        } catch (Exception e) {
            return ToolResult.fail("查询图片失败");
        }

        List<Map<String, Object>> list = new ArrayList<>();
        for (Picture picture : page.getRecords()) {
            list.add(toBrief(picture));
        }

        if (list.isEmpty()) {
            return ToolResult.ok(list, "没有找到符合条件的图片");
        }
        return ToolResult.ok(list, "共找到 " + page.getTotal() + " 张图片，本次返回 " + list.size() + " 张");
    }

    private Map<String, Object> toBrief(Picture picture) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("pictureId", picture.getId());
        item.put("name", picture.getName());
        item.put("category", picture.getCategory());
        item.put("tags", parseTags(picture.getTags()));
        item.put("url", picture.getUrl());
        item.put("thumbnailUrl", picture.getThumbnailUrl());
        item.put("spaceId", picture.getSpaceId());
        item.put("reviewStatus", picture.getReviewStatus());
        if (picture.getCreateTime() != null) {
            item.put("createTime", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(picture.getCreateTime()));
        }
        return item;
    }

    private List<String> parseTags(String tags) {
        if (StrUtil.isBlank(tags)) {
            return new ArrayList<>();
        }
        try {
            return JSONUtil.toList(tags, String.class);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    // ---------------------------------------------------------------- 参数解析

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static long clamp(Long value, long min, long max, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return Math.max(min, Math.min(max, value));
    }

    @SuppressWarnings("unchecked")
    private static List<String> asStringList(Object value) {
        if (value == null) {
            return new ArrayList<>();
        }
        if (value instanceof List) {
            List<String> result = new ArrayList<>();
            for (Object item : (List<Object>) value) {
                if (item != null && StrUtil.isNotBlank(String.valueOf(item))) {
                    result.add(String.valueOf(item));
                }
            }
            return result;
        }
        String single = String.valueOf(value);
        List<String> result = new ArrayList<>();
        if (CollUtil.isNotEmpty(StrUtil.split(single, ','))) {
            result.addAll(StrUtil.split(single, ','));
        }
        return result;
    }
}
