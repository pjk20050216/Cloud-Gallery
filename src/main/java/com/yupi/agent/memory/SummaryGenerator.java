package com.yupi.agent.memory;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.yupi.agent.config.AgentProperties;
import com.yupi.agent.llm.LlmClient;
import com.yupi.agent.llm.LlmException;
import com.yupi.agent.llm.model.ChatMessage;
import com.yupi.agent.llm.model.ChatRequest;
import com.yupi.agent.llm.model.ChatResponse;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 摘要与结构化记忆生成器。
 * <p>
 * 一次 LLM 调用同时产出「自然语言摘要」和「结构化记忆」，
 * 比调两次省一半成本。
 */
@Slf4j
@Component
public class SummaryGenerator {

    private static final String SYSTEM_PROMPT = String.join("\n",
            "你是对话压缩器。把给定的历史对话压缩成两部分：",
            "1. summary：一段第三人称的中文摘要，保留用户目标、已完成的操作与结果、未解决的问题；",
            "   丢弃寒暄、重复内容、工具参数细节。不超过 %d 字。",
            "2. memory：从对话中抽取的关键实体，只填对话里真实出现过的，不要编造。",
            "",
            "严格输出 JSON，不要 markdown 代码块，不要解释：",
            "{\"summary\":\"...\",\"memory\":{\"pictures\":[{\"id\":\"123\",\"name\":\"小猫\"}],"
                    + "\"spaces\":[{\"id\":\"456\",\"name\":\"我的空间\"}],"
                    + "\"tasks\":[\"任务号\"],\"facts\":[\"用户是管理员\"]}}",
            "",
            "注意：id 必须原样抄写，一位都不能改。没有对应内容就给空数组。");

    private static final String USER_TEMPLATE = String.join("\n",
            "【已有摘要】\n%s\n",
            "【需要合并进摘要的新对话】\n%s");

    @Resource
    private LlmClient llmClient;

    @Resource
    private AgentProperties agentProperties;

    @Data
    public static class Result {
        private String summary;
        private AgentMemory memory = new AgentMemory();
        private boolean success;
    }

    /**
     * 压缩
     *
     * @param oldSummary 已有摘要，可为空
     * @param toCompress 需要压缩掉的消息
     */
    public Result compress(String oldSummary, List<ChatMessage> toCompress) {
        Result result = new Result();
        if (toCompress == null || toCompress.isEmpty()) {
            result.setSummary(oldSummary);
            result.setSuccess(true);
            return result;
        }

        AgentProperties.Memory config = agentProperties.getMemory();
        int summaryMaxChars = config.getSummaryMaxChars() == null ? 800 : config.getSummaryMaxChars();

        String dialogue = renderDialogue(toCompress);
        String userContent = String.format(USER_TEMPLATE,
                StrUtil.blankToDefault(oldSummary, "（无）"), dialogue);

        ChatRequest request = ChatRequest.builder()
                .model(StrUtil.blankToDefault(config.getSummaryModel(), "qwen-turbo"))
                .messages(Arrays.asList(
                        ChatMessage.system(String.format(SYSTEM_PROMPT, summaryMaxChars)),
                        ChatMessage.user(userContent)))
                .tools(Collections.emptyList())
                .temperature(0.1)
                .maxTokens(Math.max(512, summaryMaxChars * 2))
                .build();

        try {
            ChatResponse response = llmClient.chat(request);
            String content = StrUtil.trim(response.getContent());
            if (StrUtil.isBlank(content)) {
                return fail(oldSummary, "摘要返回为空");
            }
            return parse(content, oldSummary, summaryMaxChars);
        } catch (LlmException e) {
            return fail(oldSummary, e.getMessage());
        } catch (Exception e) {
            return fail(oldSummary, e.getMessage());
        }
    }

    // ---------------------------------------------------------------- 内部

    private Result parse(String content, String oldSummary, int summaryMaxChars) {
        Result result = new Result();
        String text = content.replace("```json", "").replace("```", "").trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return fail(oldSummary, "摘要不是合法 JSON：" + StrUtil.maxLength(text, 100));
        }
        try {
            JSONObject object = JSONUtil.parseObj(text.substring(start, end + 1));
            String summary = object.getStr("summary");
            result.setSummary(StrUtil.maxLength(
                    StrUtil.blankToDefault(summary, oldSummary), summaryMaxChars));
            result.setMemory(parseMemory(object.getJSONObject("memory")));
            result.setSuccess(true);
            return result;
        } catch (Exception e) {
            return fail(oldSummary, "解析摘要 JSON 失败：" + e.getMessage());
        }
    }

    private AgentMemory parseMemory(JSONObject node) {
        AgentMemory memory = new AgentMemory();
        if (node == null) {
            return memory;
        }
        memory.setPictures(parseRefs(node.getJSONArray("pictures")));
        memory.setSpaces(parseRefs(node.getJSONArray("spaces")));
        memory.setTasks(parseStrings(node.getJSONArray("tasks")));
        memory.setFacts(parseStrings(node.getJSONArray("facts")));
        return memory;
    }

    private List<AgentMemory.EntityRef> parseRefs(Object arrayNode) {
        List<AgentMemory.EntityRef> list = new ArrayList<>();
        if (!(arrayNode instanceof cn.hutool.json.JSONArray)) {
            return list;
        }
        cn.hutool.json.JSONArray array = (cn.hutool.json.JSONArray) arrayNode;
        for (int i = 0; i < array.size(); i++) {
            Object item = array.get(i);
            if (item instanceof JSONObject) {
                JSONObject obj = (JSONObject) item;
                String id = obj.getStr("id");
                if (StrUtil.isNotBlank(id)) {
                    list.add(new AgentMemory.EntityRef(id, obj.getStr("name")));
                }
            } else if (item != null && StrUtil.isNotBlank(String.valueOf(item))) {
                // 模型可能直接给 id 字符串
                list.add(new AgentMemory.EntityRef(String.valueOf(item), null));
            }
        }
        return list;
    }

    private List<String> parseStrings(Object arrayNode) {
        List<String> list = new ArrayList<>();
        if (!(arrayNode instanceof cn.hutool.json.JSONArray)) {
            return list;
        }
        cn.hutool.json.JSONArray array = (cn.hutool.json.JSONArray) arrayNode;
        for (int i = 0; i < array.size(); i++) {
            Object item = array.get(i);
            if (item != null && StrUtil.isNotBlank(String.valueOf(item))) {
                list.add(String.valueOf(item));
            }
        }
        return list;
    }

    private Result fail(String oldSummary, String reason) {
        log.warn("生成摘要失败，降级为纯截断：{}", reason);
        Result result = new Result();
        result.setSummary(oldSummary);
        result.setSuccess(false);
        return result;
    }

    /**
     * 把消息渲染成紧凑的对话文本
     */
    private String renderDialogue(List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage message : messages) {
            String who = ChatMessage.ROLE_USER.equals(message.getRole()) ? "用户" : "助手";
            sb.append(who).append("：").append(StrUtil.nullToEmpty(message.getContent())).append("\n");
        }
        return sb.toString();
    }
}
