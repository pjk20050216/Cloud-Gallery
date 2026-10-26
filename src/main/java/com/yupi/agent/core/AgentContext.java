package com.yupi.agent.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 智能体执行上下文 —— 用户身份与权限的**快照**。
 * <p>
 * 必须在请求线程（Controller）里构建，之后在任意线程中只读使用。
 * 原因：RequestContextHolder、Sa-Token、HttpServletRequest 都是 ThreadLocal / 请求级对象，
 * 在 agentExecutor 线程中不可用。
 * <p>
 * 注意：这个类需要能 JSON 序列化往返（异步任务会把快照存进 agent_task.inputJson），
 * 所以字段不能用 final，必须提供无参构造。
 * 约定：构建后视为只读，不要修改。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentContext implements Serializable {

    private Long userId;

    private String userAccount;

    private String userName;

    /**
     * 是否管理员
     */
    private boolean admin;

    /**
     * 是否处于封禁中。封禁用户只能调用只读工具。
     */
    private boolean banned;

    /**
     * 封禁说明（给模型看的，避免它编造原因）
     */
    private String banMessage;

    /**
     * 权限快照（空间权限的并集）
     */
    private List<String> permissions;

    /**
     * 用户可访问的空间 id
     */
    private List<Long> accessibleSpaceIds;

    /**
     * 链路 id
     */
    private String traceId;

    public boolean hasPermission(String permission) {
        if (admin) {
            return true;
        }
        return permissions != null && permissions.contains(permission);
    }

    public boolean canAccessSpace(Long spaceId) {
        if (admin) {
            return true;
        }
        return spaceId != null && accessibleSpaceIds != null && accessibleSpaceIds.contains(spaceId);
    }

    public List<String> getPermissions() {
        return permissions == null ? Collections.emptyList() : permissions;
    }

    public List<Long> getAccessibleSpaceIds() {
        return accessibleSpaceIds == null ? Collections.emptyList() : accessibleSpaceIds;
    }
}
