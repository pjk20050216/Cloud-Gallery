package com.yupi.agent.memory;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yupi.agent.config.AgentProperties;
import com.yupi.agent.llm.model.ChatMessage;
import com.yupi.agent.session.AgentSessionStore;
import com.yupi.model.entity.AgentSession;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * 上下文压缩。
 * <p>
 * 分层上下文（按顺序注入）：
 * <pre>
 * 1. System Prompt（调用方负责）
 * 2. 结构化记忆（关键 id，不漂移）
 * 3. 滚动摘要（叙述性内容）
 * 4. 最近 N 轮原文（按 token 反推）
 * 5. 当前用户消息（调用方负责）
 * </pre>
 * 压缩是**增量**的：靠 {@code agent_session.compressedUntilId} 记录水位，
 * 只压缩还没进摘要的那批消息。
 */
@Slf4j
@Service
public class AgentMemoryService {

    @Resource
    private AgentSessionStore sessionStore;

    @Resource
    private SummaryGenerator summaryGenerator;

    @Resource
    private AgentProperties agentProperties;

    @Resource
    private ObjectMapper objectMapper;

    @Data
    public static class BuiltContext {
        /**
         * 已经分层处理好的历史（不含 system prompt 和当前用户消息）
         */
        private List<ChatMessage> messages = new ArrayList<>();

        private boolean compressed;

        private int estimatedTokens;

        /**
         * 压缩前的 token 估算，用于观测
         */
        private int beforeCompressTokens;
    }

    /**
     * 构建回灌给模型的历史上下文。
     * <p>
     * 这是 {@code AgentOrchestrator} 里替代 {@code loadHistory} 的入口。
     */
    public BuiltContext buildContext(AgentSession session, int maxRounds) {
        BuiltContext result = new BuiltContext();
        AgentProperties.Memory config = agentProperties.getMemory();

        // 1. 功能关闭 → 退回按轮数硬截断的老行为
        if (!config.isEnabled()) {
            List<ChatMessage> recent = sessionStore.loadHistory(session.getId(), maxRounds);
            int tokens = estimate(recent);
            result.setMessages(recent);
            result.setEstimatedTokens(tokens);
            result.setBeforeCompressTokens(tokens);
            return result;
        }

        // 2. 按水位加载（只取还没被压缩进摘要的部分）
        //    注意：不能用 loadHistory(maxRounds)，那会先按轮数砍掉历史，压缩逻辑根本看不到更早的消息
        List<ChatMessage> history = sessionStore.loadHistoryAfter(session.getId(), session.getCompressedUntilId());
        int before = estimate(history) + estimateMemoryBlock(session);
        result.setBeforeCompressTokens(before);
        result.setEstimatedTokens(before);

        // 3. 单条消息先截断（用户可能粘贴超长文本）
        history = truncateSingleMessages(history, positive(config.getMaxSingleMessageTokens(), 1500));

        int budget = positive(config.getMaxContextTokens(), 6000);

        // 4. 预算内直接返回（仍带上已有的记忆和摘要，保证连续性）
        int total = estimate(history) + estimateMemoryBlock(session);
        if (total <= budget) {
            result.setMessages(assemble(session, history));
            result.setEstimatedTokens(total);
            return result;
        }

        // 5. 超预算 → 增量压缩
        try {
            compress(session, config, budget);
        } catch (Exception e) {
            log.warn("上下文压缩失败，降级为纯截断：{}", e.getMessage());
        }

        // 6. 压缩后重新加载，再按保留预算截断
        List<ChatMessage> kept = truncateToBudget(
                sessionStore.loadHistoryAfter(session.getId(), session.getCompressedUntilId()),
                positive(config.getKeepRecentTokens(), 2500));

        result.setMessages(assemble(session, kept));
        result.setCompressed(true);
        result.setEstimatedTokens(estimate(result.getMessages()));
        log.info("上下文压缩完成：{} -> {} tokens", before, result.getEstimatedTokens());
        return result;
    }

    // ================================================================== 压缩

    /**
     * 增量压缩：把「水位之后、保留窗口之前」的消息压进摘要
     */
    private void compress(AgentSession session, AgentProperties.Memory config, int budget) {
        Long watermark = session.getCompressedUntilId();
        int keepTokens = positive(config.getKeepRecentTokens(), 2500);
        int maxInput = positive(config.getMaxSummaryInputTokens(), 3000);

        // 取水位之后的全部消息（带 id，用于更新水位）
        List<ChatMessage> candidates = sessionStore.loadHistoryAfter(session.getId(), watermark);
        List<Long> ids = sessionStore.loadMessageIdsAfter(session.getId(), watermark);
        log.info("压缩准备：水位={}，候选消息={} 条，id={} 条", watermark, candidates.size(), ids.size());
        if (candidates.isEmpty() || ids.size() != candidates.size()) {
            log.warn("压缩跳过：候选消息与 id 数量不匹配");
            return;
        }

        // 从尾部往前累计 token，找出需要保留的窗口起点
        int keepFrom = candidates.size();
        int acc = 0;
        for (int i = candidates.size() - 1; i >= 0; i--) {
            acc += estimateMessage(candidates.get(i));
            if (acc > keepTokens) {
                break;
            }
            keepFrom = i;
        }
        if (keepFrom <= 0) {
            // 全部都要保留，没东西可压
            log.info("压缩跳过：全部消息都在保留窗口内（keepTokens={}）", keepTokens);
            return;
        }

        // 待压缩部分（再按 maxInput 截断，防止一次塞太多）
        List<ChatMessage> toCompress = new ArrayList<>(candidates.subList(0, keepFrom));
        toCompress = trimFromEnd(toCompress, maxInput);
        if (toCompress.isEmpty()) {
            log.info("压缩跳过：待压缩内容为空");
            return;
        }
        log.info("准备压缩 {} 条消息（keepFrom={}，maxInput={}）", toCompress.size(), keepFrom, maxInput);

        // 水位只推进到"实际被压缩的最后一条"
        int compressedCount = Math.min(toCompress.size(), ids.size());
        Long newWatermark = ids.get(compressedCount - 1);

        SummaryGenerator.Result generated = summaryGenerator.compress(session.getSummary(), toCompress);
        if (!generated.isSuccess()) {
            // 摘要失败：不推进水位，下次再试；本次靠纯截断保证不超预算
            return;
        }

        AgentMemory oldMemory = AgentMemory.fromJson(session.getMemory(), objectMapper);
        AgentMemory merged = oldMemory.merge(generated.getMemory());

        sessionStore.updateSummary(session.getId(), generated.getSummary(),
                merged.toJson(objectMapper), newWatermark);

        // 同步内存中的会话对象，避免同一次请求里重复压缩
        session.setSummary(generated.getSummary());
        session.setMemory(merged.toJson(objectMapper));
        session.setCompressedUntilId(newWatermark);

        log.info("增量压缩 {} 条消息，水位推进到 {}", compressedCount, newWatermark);
    }

    // ================================================================== 组装

    /**
     * 把「记忆 + 摘要 + 原文」拼成消息列表
     */
    private List<ChatMessage> assemble(AgentSession session, List<ChatMessage> recent) {
        List<ChatMessage> result = new ArrayList<>();

        String memoryBlock = buildMemoryBlock(session);
        String summaryBlock = buildSummaryBlock(session);

        // 记忆和摘要合并成一条 system 消息，避免多条 system 干扰模型
        String context = (memoryBlock + summaryBlock).trim();
        if (StrUtil.isNotBlank(context)) {
            result.add(ChatMessage.system(context));
        }
        result.addAll(recent);
        return result;
    }

    private String buildMemoryBlock(AgentSession session) {
        AgentMemory memory = AgentMemory.fromJson(session.getMemory(), objectMapper);
        return memory.render();
    }

    private String buildSummaryBlock(AgentSession session) {
        if (StrUtil.isBlank(session.getSummary())) {
            return "";
        }
        return "【更早的对话摘要】\n" + session.getSummary() + "\n";
    }

    private int estimateMemoryBlock(AgentSession session) {
        return TokenEstimator.estimate(buildMemoryBlock(session) + buildSummaryBlock(session));
    }

    // ================================================================== 工具

    private int estimate(List<ChatMessage> messages) {
        int total = 0;
        for (ChatMessage message : messages) {
            total += estimateMessage(message);
        }
        return total;
    }

    private int estimateMessage(ChatMessage message) {
        return TokenEstimator.estimateMessage(message.getRole(), message.getContent());
    }

    /**
     * 单条消息超长时单独截断
     */
    private List<ChatMessage> truncateSingleMessages(List<ChatMessage> messages, int maxTokens) {
        List<ChatMessage> result = new ArrayList<>(messages.size());
        for (ChatMessage message : messages) {
            String content = message.getContent();
            if (content != null && TokenEstimator.estimate(content) > maxTokens) {
                String truncated = TokenEstimator.truncateToTokens(content, maxTokens);
                result.add(ChatMessage.builder()
                        .role(message.getRole())
                        .content(truncated)
                        .build());
            } else {
                result.add(message);
            }
        }
        return result;
    }

    /**
     * 从尾部保留不超过 maxTokens 的消息
     */
    private List<ChatMessage> truncateToBudget(List<ChatMessage> messages, int maxTokens) {
        if (messages.isEmpty()) {
            return messages;
        }
        int acc = 0;
        int from = messages.size();
        for (int i = messages.size() - 1; i >= 0; i--) {
            acc += estimateMessage(messages.get(i));
            if (acc > maxTokens) {
                break;
            }
            from = i;
        }
        // 至少保留最近 2 条，保证对话连贯
        from = Math.min(from, Math.max(0, messages.size() - 2));
        return new ArrayList<>(messages.subList(from, messages.size()));
    }

    /**
     * 从尾部保留不超过 maxTokens 的消息（用于压缩输入，取最老的）
     */
    private List<ChatMessage> trimFromEnd(List<ChatMessage> messages, int maxTokens) {
        int acc = 0;
        int to = messages.size();
        for (int i = 0; i < messages.size(); i++) {
            acc += estimateMessage(messages.get(i));
            if (acc > maxTokens) {
                to = i;
                break;
            }
        }
        return new ArrayList<>(messages.subList(0, to));
    }

    private int positive(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }
}
