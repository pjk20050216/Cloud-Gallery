package com.yupi.agent.tool;

import cn.hutool.core.util.StrUtil;
import com.yupi.agent.core.AgentContext;
import com.yupi.model.entity.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 工具基类：提供参数解析、用户对象还原等公共能力。
 */
public abstract class AbstractAgentTool implements AgentTool {

    // ---------------------------------------------------------------- 参数解析

    protected static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    protected static String trimToNull(Object value) {
        String text = asString(value);
        return StrUtil.isBlank(text) ? null : text.trim();
    }

    protected static Long asLong(Object value) {
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

    protected static Integer asInt(Object value) {
        Long longValue = asLong(value);
        return longValue == null ? null : longValue.intValue();
    }

    protected static boolean asBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    protected static List<String> asStringList(Object value) {
        List<String> result = new ArrayList<>();
        if (value == null) {
            return result;
        }
        if (value instanceof List) {
            for (Object item : (List<Object>) value) {
                if (item != null && StrUtil.isNotBlank(String.valueOf(item))) {
                    result.add(String.valueOf(item).trim());
                }
            }
            return result;
        }
        String text = String.valueOf(value);
        for (String part : StrUtil.split(text, ',')) {
            if (StrUtil.isNotBlank(part)) {
                result.add(part.trim());
            }
        }
        return result;
    }

    protected static long clamp(Long value, long min, long max, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return Math.max(min, Math.min(max, value));
    }

    // ---------------------------------------------------------------- 身份还原

    /**
     * 把身份快照还原成一个 User 对象，供现有 Service 使用。
     * <p>
     * 现有 Service（如 PictureServiceImpl.fileReviewParams）会调用
     * userService.isAdmin(loginUser) 判断角色，所以这里必须带上 userRole。
     */
    protected static User toUser(AgentContext context) {
        return User.builder()
                .id(context.getUserId())
                .userAccount(context.getUserAccount())
                .userName(context.getUserName())
                .userRole(context.isAdmin() ? "admin" : "user")
                .build();
    }

    // ---------------------------------------------------------------- Schema 构造

    protected static Map<String, Object> schema(Map<String, Object> properties, String... required) {
        Map<String, Object> schema = new java.util.LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", java.util.Arrays.asList(required));
        return schema;
    }

    protected static Map<String, Object> prop(String type, String description) {
        Map<String, Object> property = new java.util.LinkedHashMap<>();
        property.put("type", type);
        property.put("description", description);
        return property;
    }

    protected static Map<String, Object> arrayProp(String itemType, String description) {
        Map<String, Object> property = new java.util.LinkedHashMap<>();
        property.put("type", "array");
        property.put("description", description);
        property.put("items", prop(itemType, null));
        return property;
    }

    protected static Map<String, Object> properties() {
        return new java.util.LinkedHashMap<>();
    }
}
