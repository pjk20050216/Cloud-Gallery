package com.yupi.agent.core;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.yupi.agent.config.AgentProperties;
import com.yupi.agent.llm.LlmClient;
import com.yupi.agent.llm.model.ChatMessage;
import com.yupi.agent.llm.model.ChatRequest;
import com.yupi.agent.llm.model.ChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.Collections;

/**
 * 意图分类器（规则未命中时才调用）。
 * <p>
 * 用最小的 prompt + 小模型 + max_tokens=32，成本可以忽略。
 * 任何异常都返回 null，让上层兜底，绝不影响主流程。
 */
@Slf4j
@Component
public class AgentIntentClassifier {

    private static final String CLASSIFY_PROMPT = String.join("\n",
            "你是意图分类器。根据用户输入判断应该交给哪个智能体处理。",
            "可选智能体：",
            "- picture: 图片上传、保存、搜索、查找、改名、加标签、删除图片",
            "- space: 空间容量、已用/剩余空间、图片数量统计、空间成员",
            "- account: 账号状态、封禁、禁言、申诉、token/额度余额、会员",
            "- general: 闲聊、问候、平台怎么用、其他",
            "只输出 JSON，不要解释：{\"agent\":\"picture\",\"confidence\":0.9}",
            "confidence 取 0~1。");

    private static final java.util.List<String> VALID_AGENTS =
            Arrays.asList("picture", "space", "account", "general");

    @Resource
    private LlmClient llmClient;

    @Resource
    private AgentProperties agentProperties;

    /**
     * @return 智能体名；无法判断时返回 null
     */
    public String classify(String input) {
        if (StrUtil.isBlank(input)) {
            return null;
        }
        try {
            ChatRequest request = ChatRequest.builder()
                    .model(agentProperties.getLlm().getRouterModel())
                    .messages(Arrays.asList(
                            ChatMessage.system(CLASSIFY_PROMPT),
                            ChatMessage.user(input)))
                    .tools(Collections.emptyList())
                    .temperature(0.0)
                    .maxTokens(32)
                    .build();

            ChatResponse response = llmClient.chat(request);
            String content = StrUtil.trim(response.getContent());
            if (StrUtil.isBlank(content)) {
                return null;
            }
            String agent = parseAgent(content);
            if (agent == null || !VALID_AGENTS.contains(agent)) {
                return null;
            }
            log.debug("LLM 意图分类结果：{}，原文={}", agent, content);
            return agent;
        } catch (Exception e) {
            // 分类失败不应该影响对话，交给规则兜底
            log.warn("意图分类失败，走兜底智能体：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 兼容模型输出里可能带的 ```json 包裹或额外文字
     */
    private String parseAgent(String content) {
        String text = content.replace("```json", "").replace("```", "").trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            text = text.substring(start, end + 1);
        }
        try {
            JSONObject object = JSONUtil.parseObj(text);
            String agent = object.getStr("agent");
            Double confidence = object.getDouble("confidence");
            // 置信度太低就当作没判断出来
            if (confidence != null && confidence < 0.6) {
                return null;
            }
            return StrUtil.trim(agent);
        } catch (Exception e) {
            return null;
        }
    }
}
