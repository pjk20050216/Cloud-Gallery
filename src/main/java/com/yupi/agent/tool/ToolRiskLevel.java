package com.yupi.agent.tool;

/**
 * 工具风险等级。
 * <p>
 * P0 只做标记，P1 起用于 ToolGuard 的拦截与二次确认。
 */
public enum ToolRiskLevel {

    /**
     * 只读：不改变任何数据
     */
    READ_ONLY,

    /**
     * 写：改变数据，但可接受
     */
    WRITE,

    /**
     * 高危：删除 / 批量 / 涉及额度，必须二次确认
     */
    DANGEROUS
}
