package com.yupi.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 智能体配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    /**
     * 总开关
     */
    private boolean enabled = true;

    private Llm llm = new Llm();

    private Chat chat = new Chat();

    private Confirm confirm = new Confirm();

    @Data
    public static class Confirm {

        /**
         * 确认令牌有效期（秒）
         */
        private Integer tokenTtlSeconds = 300;

        /**
         * 幂等键有效期（秒）
         */
        private Integer idempotencyTtlSeconds = 600;

        /**
         * 待确认的推理现场保留时长（秒）
         */
        private Integer pendingTurnTtlSeconds = 600;
    }

    @Data
    public static class Llm {

        /**
         * API Key，为空时回退到 aliYunOssAi.apiKey
         */
        private String apiKey;

        /**
         * OpenAI 兼容模式地址（不带 /chat/completions）
         */
        private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";

        /**
         * 意图分类用的轻量模型（P2 启用）
         */
        private String routerModel = "qwen-turbo";

        /**
         * 专家智能体默认模型
         */
        private String defaultModel = "qwen-plus";

        private Integer timeoutSeconds = 60;

        private Integer maxRetries = 1;

        private Double temperature = 0.3;

        private Integer maxTokens = 2048;
    }

    @Data
    public static class Chat {

        /**
         * 单轮对话最多工具调用轮次
         */
        private Integer maxSteps = 8;

        /**
         * 携带的历史轮数
         */
        private Integer maxHistoryRounds = 10;

        /**
         * 工具结果回灌给模型时的最大字符数
         */
        private Integer toolResultMaxChars = 500;

        /**
         * SSE 超时（毫秒）
         */
        private Long sseTimeoutMs = 180000L;

        /**
         * 规则未命中时是否启用 LLM 意图分类
         */
        private boolean enableLlmRouter = true;

        /**
         * 单轮对话最多转交次数，防止智能体之间乒乓
         */
        private Integer maxHandoffHops = 2;
    }

    private Task task = new Task();

    private Memory memory = new Memory();

    @Data
    public static class Memory {

        /**
         * 总开关。关掉后退回"按轮数硬截断"的老逻辑
         */
        private boolean enabled = true;

        /**
         * 回灌给模型的历史上下文 token 预算，超过就触发压缩
         */
        private Integer maxContextTokens = 6000;

        /**
         * 压缩时保留多少 token 的最近原文
         */
        private Integer keepRecentTokens = 2500;

        /**
         * 摘要最大字符数
         */
        private Integer summaryMaxChars = 800;

        /**
         * 摘要用的模型（便宜的小模型即可）
         */
        private String summaryModel = "qwen-turbo";

        /**
         * 送去压缩的输入上限，防止一次塞太多
         */
        private Integer maxSummaryInputTokens = 3000;

        /**
         * 单条消息的 token 上限，超长消息会被单独截断
         */
        private Integer maxSingleMessageTokens = 1500;
    }

    @Data
    public static class Task {

        /**
         * 每个用户同时只能跑一个异步任务
         */
        private Integer userConcurrency = 1;

        /**
         * 用户级并发锁的过期时间（秒）。
         * 进程崩溃时靠它自愈，避免用户被永久锁住。
         */
        private Integer lockTtlSeconds = 900;

        /**
         * 单个任务最长执行时间（秒），超过则标记失败
         */
        private Integer timeoutSeconds = 300;

        /**
         * 批量上传单次最多抓取数量
         */
        private Integer batchUploadMaxCount = 30;

        /**
         * 任务列表默认返回条数
         */
        private Integer listLimit = 20;
    }
}
