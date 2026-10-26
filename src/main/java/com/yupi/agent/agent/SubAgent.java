package com.yupi.agent.agent;

import java.util.List;

/**
 * 子智能体：拥有独立的角色设定、工具白名单与步数上限。
 */
public interface SubAgent {

    /**
     * 唯一标识，如 general / picture
     */
    String name();

    /**
     * 展示名，如「通用助手」
     */
    String displayName();

    /**
     * 给路由器看的一句话描述（P2 多智能体路由时使用）
     */
    String description();

    /**
     * 角色设定
     */
    String systemPrompt();

    /**
     * 工具白名单
     */
    List<String> toolNames();

    /**
     * 单轮最多工具调用轮次
     */
    default int maxSteps() {
        return 6;
    }

    /**
     * 指定模型；返回 null 表示用默认模型
     */
    default String model() {
        return null;
    }
}
