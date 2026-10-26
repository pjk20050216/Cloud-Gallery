package com.yupi.agent.tool;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具执行结果
 */
@Data
@Builder
public class ToolResult implements Serializable {

    private boolean success;

    /**
     * 结构化数据，最终会序列化后回灌给模型
     */
    private Object data;

    /**
     * 给模型看的简短说明（比原始数据更省 token，也更不容易跑偏）
     */
    private String summary;

    private String error;

    public static ToolResult ok(Object data, String summary) {
        return ToolResult.builder().success(true).data(data).summary(summary).build();
    }

    public static ToolResult ok(Object data) {
        return ToolResult.builder().success(true).data(data).build();
    }

    public static ToolResult fail(String error) {
        return ToolResult.builder().success(false).error(error).build();
    }

    /**
     * 转成回灌给模型的一段文本。
     * <p>
     * 一定要截断：一次图片列表查询可能返回几千 token，会把上下文撑爆。
     */
    public String toLlmString(int maxChars) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", success);
        if (StrUtil.isNotBlank(summary)) {
            payload.put("summary", summary);
        }
        if (success) {
            payload.put("data", data);
        } else {
            payload.put("error", StrUtil.blankToDefault(error, "执行失败"));
        }
        String json;
        try {
            json = JSONUtil.toJsonStr(payload);
        } catch (Exception e) {
            json = String.valueOf(payload);
        }
        return StrUtil.maxLength(json, maxChars);
    }
}
