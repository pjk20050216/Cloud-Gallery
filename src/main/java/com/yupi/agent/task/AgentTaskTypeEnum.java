package com.yupi.agent.task;

/**
 * 任务类型
 */
public enum AgentTaskTypeEnum {

    /**
     * 一次完整的异步对话
     */
    CHAT,

    /**
     * 批量抓取并上传图片
     */
    BATCH_UPLOAD
}
