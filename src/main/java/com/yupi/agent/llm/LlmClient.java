package com.yupi.agent.llm;

import com.yupi.agent.llm.model.ChatRequest;
import com.yupi.agent.llm.model.ChatResponse;

import java.util.function.Consumer;

/**
 * LLM 客户端抽象。默认实现为 {@link DashScopeLlmClient}（OpenAI 兼容协议），
 * 后续要换模型厂商，只需新增一个实现即可。
 */
public interface LlmClient {

    /**
     * 非流式调用
     */
    ChatResponse chat(ChatRequest request);

    /**
     * 流式调用。文本增量通过 onDelta 回调抛出；
     * 返回的 ChatResponse 中仍然包含累积后的完整内容与工具调用。
     *
     * @param onDelta 文本增量回调，可为 null（为 null 时退化为非流式）
     */
    ChatResponse streamChat(ChatRequest request, Consumer<String> onDelta);
}
