package com.yupi.agent.llm;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.yupi.agent.config.AgentProperties;
import com.yupi.agent.llm.model.ChatMessage;
import com.yupi.agent.llm.model.ChatRequest;
import com.yupi.agent.llm.model.ChatResponse;
import com.yupi.agent.llm.model.ToolCall;
import com.yupi.agent.llm.model.ToolSpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * 基于阿里云百炼（DashScope）OpenAI 兼容协议的实现。
 * <p>
 * 之所以走 OpenAI 兼容协议而不是 dashscope-sdk：
 * 报文结构完全可控、流式与 function calling 的行为稳定、后续换厂商成本最低。
 */
@Slf4j
@Component
public class DashScopeLlmClient implements LlmClient {

    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

    @Resource
    private AgentProperties agentProperties;

    /**
     * 回退用的密钥（复用项目已有的阿里云配置）
     */
    @Value("${aliYunOssAi.apiKey:}")
    private String fallbackApiKey;

    @Override
    public ChatResponse chat(ChatRequest request) {
        return doChat(request, null);
    }

    @Override
    public ChatResponse streamChat(ChatRequest request, Consumer<String> onDelta) {
        return doChat(request, onDelta);
    }

    private ChatResponse doChat(ChatRequest request, Consumer<String> onDelta) {
        AgentProperties.Llm llm = agentProperties.getLlm();
        String apiKey = resolveApiKey();
        if (StrUtil.isBlank(apiKey)) {
            throw new LlmException("未配置 AI 密钥，请设置 DASHSCOPE_API_KEY 或 aliYunOssAi.apiKey", false);
        }

        String model = StrUtil.blankToDefault(request.getModel(), llm.getDefaultModel());
        boolean stream = onDelta != null;
        String body = buildRequestBody(request, model, stream);
        String url = StrUtil.removeSuffix(llm.getBaseUrl(), "/") + CHAT_COMPLETIONS_PATH;

        int maxRetries = llm.getMaxRetries() == null ? 0 : Math.max(0, llm.getMaxRetries());
        LlmException lastError = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return stream ? doStream(url, apiKey, body, onDelta) : doSync(url, apiKey, body);
            } catch (LlmException e) {
                lastError = e;
                if (!e.isRetryable()) {
                    throw e;
                }
                log.warn("LLM 调用失败，第 {} 次重试：{}", attempt + 1, e.getMessage());
            }
        }
        throw lastError == null ? new LlmException("LLM 调用失败") : lastError;
    }

    private String resolveApiKey() {
        String key = agentProperties.getLlm().getApiKey();
        return StrUtil.isNotBlank(key) ? key : fallbackApiKey;
    }

    // ------------------------------------------------------------------ 请求构造

    private String buildRequestBody(ChatRequest request, String model, boolean stream) {
        AgentProperties.Llm llm = agentProperties.getLlm();
        JSONObject root = JSONUtil.createObj();
        root.set("model", model);
        root.set("messages", buildMessages(request.getMessages()));
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            root.set("tools", buildTools(request.getTools()));
        }
        root.set("temperature", request.getTemperature() != null ? request.getTemperature() : llm.getTemperature());
        Integer maxTokens = request.getMaxTokens() != null ? request.getMaxTokens() : llm.getMaxTokens();
        if (maxTokens != null && maxTokens > 0) {
            root.set("max_tokens", maxTokens);
        }
        root.set("stream", stream);
        return root.toString();
    }

    private JSONArray buildMessages(List<ChatMessage> messages) {
        JSONArray array = new JSONArray();
        if (messages == null) {
            return array;
        }
        for (ChatMessage message : messages) {
            JSONObject item = JSONUtil.createObj();
            item.set("role", message.getRole());

            if (ChatMessage.ROLE_ASSISTANT.equals(message.getRole())
                    && message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
                // 带工具调用的 assistant 消息：content 必须是字符串（可以为空串）
                item.set("content", StrUtil.nullToEmpty(message.getContent()));
                JSONArray toolCalls = new JSONArray();
                for (ToolCall call : message.getToolCalls()) {
                    toolCalls.add(JSONUtil.createObj()
                            .set("id", call.getId())
                            .set("type", "function")
                            .set("function", JSONUtil.createObj()
                                    .set("name", call.getName())
                                    .set("arguments", StrUtil.nullToEmpty(call.getArguments()))));
                }
                item.set("tool_calls", toolCalls);
            } else if (ChatMessage.ROLE_TOOL.equals(message.getRole())) {
                item.set("tool_call_id", message.getToolCallId());
                item.set("content", StrUtil.nullToEmpty(message.getContent()));
            } else {
                item.set("content", StrUtil.nullToEmpty(message.getContent()));
            }
            array.add(item);
        }
        return array;
    }

    private JSONArray buildTools(List<ToolSpec> tools) {
        JSONArray array = new JSONArray();
        for (ToolSpec spec : tools) {
            JSONObject function = JSONUtil.createObj()
                    .set("name", spec.getName())
                    .set("description", spec.getDescription())
                    .set("parameters", spec.getParameters());
            array.add(JSONUtil.createObj().set("type", "function").set("function", function));
        }
        return array;
    }

    // ------------------------------------------------------------------ 非流式

    private ChatResponse doSync(String url, String apiKey, String body) {
        try (HttpResponse response = HttpRequest.post(url)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(body)
                .timeout(timeoutMillis())
                .execute()) {

            if (!response.isOk()) {
                throw toHttpError(response);
            }
            JSONObject root = JSONUtil.parseObj(response.body());
            JSONArray choices = root.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw new LlmException("AI 返回内容为空");
            }
            JSONObject choice = choices.getJSONObject(0);
            JSONObject message = choice.getJSONObject("message");
            ChatResponse.ChatResponseBuilder builder = ChatResponse.builder()
                    .finishReason(choice.getStr("finish_reason"))
                    .content(message == null ? null : message.getStr("content"))
                    .toolCalls(message == null ? null : parseToolCalls(message.getJSONArray("tool_calls")));
            applyUsage(root, builder);
            return builder.build();
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("AI 服务调用失败：" + e.getMessage(), true, e);
        }
    }

    // ------------------------------------------------------------------ 流式

    private ChatResponse doStream(String url, String apiKey, String body, Consumer<String> onDelta) {
        HttpResponse response = null;
        try {
            response = HttpRequest.post(url)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .body(body)
                    .timeout(timeoutMillis())
                    .executeAsync();

            if (!response.isOk()) {
                throw toHttpError(response);
            }
            return parseStream(response.bodyStream(), onDelta);
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("AI 流式调用失败：" + e.getMessage(), true, e);
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    private ChatResponse parseStream(InputStream inputStream, Consumer<String> onDelta) throws Exception {
        StringBuilder content = new StringBuilder();
        // 按 index 聚合流式返回的 tool_calls 分片
        Map<Integer, ToolCallAccumulator> accumulatorMap = new TreeMap<>();
        String finishReason = null;
        int promptTokens = 0;
        int completionTokens = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || !line.startsWith("data:")) {
                    continue;
                }
                String payload = line.substring("data:".length()).trim();
                if (payload.isEmpty()) {
                    continue;
                }
                if ("[DONE]".equals(payload)) {
                    break;
                }

                JSONObject chunk;
                try {
                    chunk = JSONUtil.parseObj(payload);
                } catch (Exception ignore) {
                    // 跳过无法解析的分片，避免整个流中断
                    continue;
                }

                JSONObject usage = chunk.getJSONObject("usage");
                if (usage != null) {
                    promptTokens = usage.getInt("prompt_tokens", promptTokens);
                    completionTokens = usage.getInt("completion_tokens", completionTokens);
                }

                JSONArray choices = chunk.getJSONArray("choices");
                if (choices == null || choices.isEmpty()) {
                    continue;
                }
                JSONObject choice = choices.getJSONObject(0);
                if (StrUtil.isNotBlank(choice.getStr("finish_reason"))) {
                    finishReason = choice.getStr("finish_reason");
                }
                JSONObject delta = choice.getJSONObject("delta");
                if (delta == null) {
                    continue;
                }

                String piece = delta.getStr("content");
                if (StrUtil.isNotEmpty(piece)) {
                    content.append(piece);
                    onDelta.accept(piece);
                }

                JSONArray toolCalls = delta.getJSONArray("tool_calls");
                if (toolCalls != null) {
                    for (int i = 0; i < toolCalls.size(); i++) {
                        JSONObject item = toolCalls.getJSONObject(i);
                        int index = item.getInt("index", i);
                        ToolCallAccumulator accumulator =
                                accumulatorMap.computeIfAbsent(index, k -> new ToolCallAccumulator());
                        String id = item.getStr("id");
                        if (StrUtil.isNotEmpty(id)) {
                            accumulator.id = id;
                        }
                        JSONObject function = item.getJSONObject("function");
                        if (function != null) {
                            String name = function.getStr("name");
                            if (StrUtil.isNotEmpty(name)) {
                                accumulator.name = name;
                            }
                            String arguments = function.getStr("arguments");
                            if (arguments != null) {
                                accumulator.arguments.append(arguments);
                            }
                        }
                    }
                }
            }
        }

        List<ToolCall> toolCalls = new ArrayList<>();
        for (ToolCallAccumulator accumulator : accumulatorMap.values()) {
            if (StrUtil.isNotBlank(accumulator.name)) {
                toolCalls.add(ToolCall.builder()
                        .id(StrUtil.blankToDefault(accumulator.id, "call_" + toolCalls.size()))
                        .name(accumulator.name)
                        .arguments(accumulator.arguments.toString())
                        .build());
            }
        }

        return ChatResponse.builder()
                .content(content.length() == 0 ? null : content.toString())
                .toolCalls(toolCalls.isEmpty() ? null : toolCalls)
                .finishReason(finishReason)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .build();
    }

    private static class ToolCallAccumulator {
        private String id;
        private String name;
        private final StringBuilder arguments = new StringBuilder();
    }

    // ------------------------------------------------------------------ 公共

    private List<ToolCall> parseToolCalls(JSONArray array) {
        if (array == null || array.isEmpty()) {
            return null;
        }
        List<ToolCall> toolCalls = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            JSONObject item = array.getJSONObject(i);
            JSONObject function = item.getJSONObject("function");
            if (function == null) {
                continue;
            }
            toolCalls.add(ToolCall.builder()
                    .id(StrUtil.blankToDefault(item.getStr("id"), "call_" + i))
                    .name(function.getStr("name"))
                    .arguments(function.getStr("arguments"))
                    .build());
        }
        return toolCalls.isEmpty() ? null : toolCalls;
    }

    private void applyUsage(JSONObject root, ChatResponse.ChatResponseBuilder builder) {
        JSONObject usage = root.getJSONObject("usage");
        if (usage != null) {
            builder.promptTokens(usage.getInt("prompt_tokens", 0));
            builder.completionTokens(usage.getInt("completion_tokens", 0));
        }
    }

    private int timeoutMillis() {
        Integer seconds = agentProperties.getLlm().getTimeoutSeconds();
        return (seconds == null ? 60 : seconds) * 1000;
    }

    private LlmException toHttpError(HttpResponse response) {
        int status = response.getStatus();
        String body = safeBody(response);
        log.error("LLM 请求失败，status={}，body={}", status, body);
        // 429 限流 / 5xx 服务端错误 / 408 超时 → 可重试
        boolean retryable = status == 429 || status == 408 || status >= 500;
        String message = status == 401 || status == 403
                ? "AI 密钥无效或已过期，请检查配置"
                : "AI 服务暂时不可用（HTTP " + status + "）";
        return new LlmException(message, retryable);
    }

    private String safeBody(HttpResponse response) {
        try {
            String body = response.body();
            return StrUtil.maxLength(body, 500);
        } catch (Exception e) {
            return "<unreadable>";
        }
    }

    /**
     * 供测试/调试：把消息列表转成可读文本
     */
    public static String dumpMessages(List<ChatMessage> messages) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("messages", messages);
        return JSONUtil.toJsonStr(map);
    }
}
