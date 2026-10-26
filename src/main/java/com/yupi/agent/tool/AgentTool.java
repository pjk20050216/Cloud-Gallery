package com.yupi.agent.tool;

import com.yupi.agent.core.AgentContext;

import java.util.Map;

/**
 * 智能体工具契约。
 * <p>
 * 实现类直接标注 {@code @Component} 即可被 {@link ToolRegistry} 自动收集。
 * <p>
 * 注意：
 * <ul>
 *     <li>入参 args 完全来自模型，**必须视为不可信输入**，要做类型与范围校验；</li>
 *     <li>用户身份只能从 {@link AgentContext} 取，绝不允许从 args 里读 userId；</li>
 *     <li>方法签名禁止出现 HttpServletRequest / MultipartFile —— 工具可能在异步线程执行。</li>
 * </ul>
 */
public interface AgentTool {

    /**
     * 全局唯一名，建议 `域.动作`，如 picture.search
     */
    String name();

    /**
     * 给模型看的说明：做什么、什么时候用、什么时候不要用
     */
    String description();

    /**
     * JSON Schema
     */
    Map<String, Object> parameters();

    ToolRiskLevel risk();

    /**
     * 执行该工具需要的空间权限（SpaceUserPermissionConstant 中的值）。
     * <p>
     * 返回 null 表示不校验空间权限（例如公共图库的只读查询），
     * 此时工具内部必须自行做归属校验。
     */
    default String requiredPermission() {
        return null;
    }

    /**
     * 用户处于封禁状态时，该工具是否仍然允许调用。
     * <p>
     * 默认不允许；只有"查看自己的处罚记录""提交申诉"这类工具才应返回 true。
     */
    default boolean allowedWhenBanned() {
        return false;
    }

    ToolResult execute(AgentContext context, Map<String, Object> args);
}
