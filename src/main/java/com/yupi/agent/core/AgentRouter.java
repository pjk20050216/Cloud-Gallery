package com.yupi.agent.core;

import cn.hutool.core.util.StrUtil;
import com.yupi.agent.agent.SubAgent;
import com.yupi.agent.agent.SubAgentRegistry;
import com.yupi.agent.config.AgentProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 意图路由：三级策略。
 * <ol>
 *     <li><b>规则命中</b>：0 次 LLM 调用，覆盖绝大多数常见说法</li>
 *     <li><b>LLM 分类</b>：小模型 + 32 token，规则没命中时才走</li>
 *     <li><b>兜底</b>：通用助手</li>
 * </ol>
 */
@Slf4j
@Component
public class AgentRouter {

    /**
     * 账号域关键词（优先级最高，"账号被封"不能被"图片"抢走）
     */
    private static final String[] ACCOUNT_WORDS = {
            "封禁", "被封", "封号", "封了", "禁言", "申诉", "解封",
            "账号", "帐号", "会员", "额度", "余额", "token", "Token", "TOKEN"
    };

    /**
     * 图片域关键词
     */
    private static final String[] PICTURE_WORDS = {
            "上传", "传图", "传到图库", "保存这张", "存起来", "导入",
            "图片", "照片", "图库", "找图", "搜图", "搜索", "查找",
            "改名", "重命名", "标签", "分类", "删除", "删掉", "删了", "扩图"
    };

    /**
     * 空间域关键词
     */
    private static final String[] SPACE_WORDS = {
            "空间", "容量", "占用", "用了多少", "剩余空间", "配额", "统计", "成员"
    };

    @Resource
    private SubAgentRegistry subAgentRegistry;

    @Resource
    private AgentIntentClassifier agentIntentClassifier;

    @Resource
    private AgentProperties agentProperties;

    public SubAgent route(AgentContext context, String input) {
        // 1. 规则命中
        String byRule = matchByRule(input);
        if (byRule != null) {
            SubAgent agent = subAgentRegistry.get(byRule);
            if (agent != null) {
                log.debug("路由命中规则：{} -> {}", StrUtil.maxLength(input, 30), byRule);
                return agent;
            }
        }

        // 2. LLM 分类
        if (agentProperties.getChat().isEnableLlmRouter()) {
            String byLlm = agentIntentClassifier.classify(input);
            if (byLlm != null) {
                SubAgent agent = subAgentRegistry.get(byLlm);
                if (agent != null) {
                    return agent;
                }
            }
        }

        // 3. 兜底
        return subAgentRegistry.getDefault();
    }

    /**
     * 规则匹配，返回智能体名；未命中返回 null
     */
    String matchByRule(String input) {
        if (StrUtil.isBlank(input)) {
            return null;
        }
        if (containsAny(input, ACCOUNT_WORDS)) {
            return "account";
        }
        if (containsAny(input, PICTURE_WORDS)) {
            return "picture";
        }
        if (containsAny(input, SPACE_WORDS)) {
            return "space";
        }
        return null;
    }

    private boolean containsAny(String input, String[] words) {
        for (String word : words) {
            if (input.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
