package com.yupi.agent.llm;

/**
 * LLM 调用异常
 */
public class LlmException extends RuntimeException {

    /**
     * 是否可重试（超时、限流、网络抖动等）
     */
    private final boolean retryable;

    public LlmException(String message) {
        this(message, false, null);
    }

    public LlmException(String message, boolean retryable) {
        this(message, retryable, null);
    }

    public LlmException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
