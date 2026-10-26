package com.yupi.agent.tool;

import java.util.Map;

/**
 * 需要用户二次确认才能执行的高危操作。
 * <p>
 * 由 {@link ToolGuard} 抛出，由编排器捕获并生成确认令牌。
 */
public class ConfirmRequiredException extends RuntimeException {

    private final String toolName;

    private final Map<String, Object> args;

    private final String preview;

    public ConfirmRequiredException(String toolName, Map<String, Object> args, String preview) {
        super(preview);
        this.toolName = toolName;
        this.args = args;
        this.preview = preview;
    }

    public String getToolName() {
        return toolName;
    }

    public Map<String, Object> getArgs() {
        return args;
    }

    public String getPreview() {
        return preview;
    }
}
