package com.yupi.agent.task;

/**
 * 任务状态
 */
public enum AgentTaskStatusEnum {

    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELED;

    public static boolean isFinished(String status) {
        return SUCCESS.name().equals(status)
                || FAILED.name().equals(status)
                || CANCELED.name().equals(status);
    }

    public static boolean isRunning(String status) {
        return PENDING.name().equals(status) || RUNNING.name().equals(status);
    }
}
