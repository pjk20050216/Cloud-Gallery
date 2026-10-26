package com.yupi.agent.core;

/**
 * 智能体执行异常（非业务异常，用于中断流程）
 */
public class AgentException extends RuntimeException {

    public AgentException(String message) {
        super(message);
    }

    public AgentException(String message, Throwable cause) {
        super(message, cause);
    }
}
