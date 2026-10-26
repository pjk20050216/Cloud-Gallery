package com.yupi.agent.session;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yupi.agent.config.AgentProperties;
import com.yupi.agent.llm.model.ChatMessage;
import com.yupi.mapper.AgentMessageMapper;
import com.yupi.mapper.AgentSessionMapper;
import com.yupi.model.entity.AgentMessage;
import com.yupi.model.entity.AgentSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 会话与消息存储。
 * <p>
 * MySQL 为唯一事实来源；Redis 只做最近消息的热缓存，写时失效、读时回填。
 */
@Slf4j
@Service
public class AgentSessionStore {

    private static final String RECENT_KEY_PREFIX = "agent:session:recent:";

    private static final long RECENT_TTL_HOURS = 2L;

    @Resource
    private AgentSessionMapper agentSessionMapper;

    @Resource
    private AgentMessageMapper agentMessageMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private AgentProperties agentProperties;

    // ---------------------------------------------------------------- 会话

    /**
     * 获取或创建会话。传入的 sessionId 不属于当前用户时，忽略并新建，避免越权读取。
     */
    public AgentSession getOrCreate(Long userId, Long sessionId, String firstMessage) {
        if (sessionId != null) {
            AgentSession existing = agentSessionMapper.selectById(sessionId);
            if (existing != null && userId.equals(existing.getUserId())) {
                return existing;
            }
            log.warn("会话 {} 不属于用户 {}，已忽略并新建", sessionId, userId);
        }

        AgentSession session = new AgentSession();
        session.setUserId(userId);
        session.setTitle(StrUtil.maxLength(StrUtil.blankToDefault(firstMessage, "新会话"), 30));
        session.setAgentType("general");
        session.setMessageCount(0);
        session.setLastMessageTime(new Date());
        session.setCreateTime(new Date());
        session.setUpdateTime(new Date());
        agentSessionMapper.insert(session);
        return session;
    }

    public AgentSession getOwned(Long userId, Long sessionId) {
        AgentSession session = agentSessionMapper.selectById(sessionId);
        if (session == null || !userId.equals(session.getUserId())) {
            return null;
        }
        return session;
    }

    public List<AgentSession> listSessions(Long userId, int limit) {
        return agentSessionMapper.selectList(new LambdaQueryWrapper<AgentSession>()
                .eq(AgentSession::getUserId, userId)
                .orderByDesc(AgentSession::getUpdateTime)
                .last("limit " + Math.max(1, Math.min(limit, 100))));
    }

    /**
     * 更新会话的最后活跃时间与消息计数
     */
    public void touch(AgentSession session, int addedMessages) {
        AgentSession update = new AgentSession();
        update.setId(session.getId());
        update.setMessageCount((session.getMessageCount() == null ? 0 : session.getMessageCount()) + addedMessages);
        update.setLastMessageTime(new Date());
        update.setUpdateTime(new Date());
        agentSessionMapper.updateById(update);
        session.setMessageCount(update.getMessageCount());
    }

    /**
     * 更新会话当前智能体
     */
    public void updateAgentType(Long sessionId, String agentType) {
        if (sessionId == null || StrUtil.isBlank(agentType)) {
            return;
        }
        AgentSession update = new AgentSession();
        update.setId(sessionId);
        update.setAgentType(agentType);
        update.setUpdateTime(new Date());
        agentSessionMapper.updateById(update);
    }

    // ---------------------------------------------------------------- 消息

    /**
     * 删除会话（逻辑删除）及其消息
     */
    public boolean deleteSession(Long userId, Long sessionId) {
        AgentSession session = getOwned(userId, sessionId);
        if (session == null) {
            return false;
        }
        agentSessionMapper.deleteById(sessionId);
        agentMessageMapper.delete(new LambdaQueryWrapper<AgentMessage>()
                .eq(AgentMessage::getSessionId, sessionId));
        invalidateCache(sessionId);
        return true;
    }

    /**
     * 加载历史对话（只取 user / assistant 的纯文本消息，工具消息不回灌，节省上下文）
     */
    public List<ChatMessage> loadHistory(Long sessionId, int maxRounds) {
        List<ChatMessage> cached = readCache(sessionId);
        if (cached != null) {
            return trimRounds(cached, maxRounds);
        }

        int limit = Math.max(1, maxRounds) * 2;
        List<AgentMessage> records = agentMessageMapper.selectList(new LambdaQueryWrapper<AgentMessage>()
                .eq(AgentMessage::getSessionId, sessionId)
                .in(AgentMessage::getRole, Arrays.asList(ChatMessage.ROLE_USER, ChatMessage.ROLE_ASSISTANT))
                .isNotNull(AgentMessage::getContent)
                .ne(AgentMessage::getContent, "")
                .orderByDesc(AgentMessage::getId)
                .last("limit " + limit));

        Collections.reverse(records);
        List<ChatMessage> history = records.stream()
                .map(m -> ChatMessage.builder().role(m.getRole()).content(m.getContent()).build())
                .collect(Collectors.toList());

        writeCache(sessionId, history);
        return history;
    }

    /**
     * 按水位加载历史：只取 id 大于 afterId 的消息（用于增量压缩）
     *
     * @param afterId 水位，null 表示从头
     */
    public List<ChatMessage> loadHistoryAfter(Long sessionId, Long afterId) {
        List<AgentMessage> records = agentMessageMapper.selectList(new LambdaQueryWrapper<AgentMessage>()
                .eq(AgentMessage::getSessionId, sessionId)
                .in(AgentMessage::getRole, Arrays.asList(ChatMessage.ROLE_USER, ChatMessage.ROLE_ASSISTANT))
                .isNotNull(AgentMessage::getContent)
                .ne(AgentMessage::getContent, "")
                .gt(afterId != null, AgentMessage::getId, afterId)
                .orderByAsc(AgentMessage::getId));

        return records.stream()
                .map(m -> ChatMessage.builder().role(m.getRole()).content(m.getContent()).build())
                .collect(Collectors.toList());
    }

    /**
     * 加载历史消息的 id（与 {@link #loadHistoryAfter} 一一对应），用于更新水位
     */
    public List<Long> loadMessageIdsAfter(Long sessionId, Long afterId) {
        List<AgentMessage> records = agentMessageMapper.selectList(new LambdaQueryWrapper<AgentMessage>()
                .eq(AgentMessage::getSessionId, sessionId)
                .in(AgentMessage::getRole, Arrays.asList(ChatMessage.ROLE_USER, ChatMessage.ROLE_ASSISTANT))
                .isNotNull(AgentMessage::getContent)
                .ne(AgentMessage::getContent, "")
                .gt(afterId != null, AgentMessage::getId, afterId)
                .orderByAsc(AgentMessage::getId)
                .select(AgentMessage::getId));

        return records.stream().map(AgentMessage::getId).collect(Collectors.toList());
    }

    /**
     * 更新摘要与结构化记忆
     */
    public void updateSummary(Long sessionId, String summary, String memoryJson, Long compressedUntilId) {
        AgentSession update = new AgentSession();
        update.setId(sessionId);
        update.setSummary(summary);
        update.setMemory(memoryJson);
        update.setCompressedUntilId(compressedUntilId);
        update.setUpdateTime(new Date());
        agentSessionMapper.updateById(update);
        invalidateCache(sessionId);
    }

    public AgentMessage saveUserMessage(Long sessionId, Long userId, String content) {
        return save(sessionId, userId, ChatMessage.ROLE_USER, null, content, null, null, null, null);
    }

    public AgentMessage saveAssistantMessage(Long sessionId, Long userId, String agentType, String content,
                                             int promptTokens, int completionTokens, int latencyMs) {
        AgentMessage message = save(sessionId, userId, ChatMessage.ROLE_ASSISTANT, agentType, content,
                null, null, null, null);
        AgentMessage update = new AgentMessage();
        update.setId(message.getId());
        update.setPromptTokens(promptTokens);
        update.setCompletionTokens(completionTokens);
        update.setLatencyMs(latencyMs);
        agentMessageMapper.updateById(update);
        return message;
    }

    public void saveToolMessage(Long sessionId, Long userId, String agentType, String toolName,
                                String toolCallId, String args, String result, boolean success, int costMs) {
        save(sessionId, userId, ChatMessage.ROLE_TOOL, agentType, null,
                toolName, toolCallId, args, result, success ? "SUCCESS" : "FAILED", costMs);
    }

    private AgentMessage save(Long sessionId, Long userId, String role, String agentType, String content,
                              String toolName, String toolCallId, String args, String result) {
        return save(sessionId, userId, role, agentType, content, toolName, toolCallId, args, result, "SUCCESS", 0);
    }

    private AgentMessage save(Long sessionId, Long userId, String role, String agentType, String content,
                              String toolName, String toolCallId, String args, String result,
                              String status, int costMs) {
        AgentMessage message = new AgentMessage();
        message.setSessionId(sessionId);
        message.setUserId(userId);
        message.setRole(role);
        message.setAgentType(agentType);
        message.setContent(content);
        message.setToolName(toolName);
        message.setToolCallId(toolCallId);
        message.setToolArgs(args);
        message.setToolResult(result);
        message.setStatus(status);
        message.setPromptTokens(0);
        message.setCompletionTokens(0);
        message.setLatencyMs(costMs);
        message.setCreateTime(new Date());
        agentMessageMapper.insert(message);
        invalidateCache(sessionId);
        return message;
    }

    public List<AgentMessage> listMessages(Long sessionId, long current, long pageSize) {
        long offset = Math.max(0, (current - 1) * pageSize);
        return agentMessageMapper.selectList(new LambdaQueryWrapper<AgentMessage>()
                .eq(AgentMessage::getSessionId, sessionId)
                .orderByAsc(AgentMessage::getId)
                .last("limit " + offset + "," + Math.max(1, Math.min(pageSize, 100))));
    }

    // ---------------------------------------------------------------- 缓存

    private List<ChatMessage> readCache(Long sessionId) {
        try {
            String json = stringRedisTemplate.opsForValue().get(RECENT_KEY_PREFIX + sessionId);
            if (StrUtil.isBlank(json)) {
                return null;
            }
            return JSONUtil.toList(json, ChatMessage.class);
        } catch (Exception e) {
            log.warn("读取会话缓存失败：{}", e.getMessage());
            return null;
        }
    }

    private void writeCache(Long sessionId, List<ChatMessage> history) {
        try {
            String key = RECENT_KEY_PREFIX + sessionId;
            if (history == null || history.isEmpty()) {
                stringRedisTemplate.delete(key);
                return;
            }
            stringRedisTemplate.opsForValue()
                    .set(key, JSONUtil.toJsonStr(history), RECENT_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("写入会话缓存失败：{}", e.getMessage());
        }
    }

    private void invalidateCache(Long sessionId) {
        try {
            stringRedisTemplate.delete(RECENT_KEY_PREFIX + sessionId);
        } catch (Exception e) {
            log.warn("清理会话缓存失败：{}", e.getMessage());
        }
    }

    private List<ChatMessage> trimRounds(List<ChatMessage> messages, int maxRounds) {
        int maxSize = Math.max(1, maxRounds) * 2;
        if (messages.size() <= maxSize) {
            return new ArrayList<>(messages);
        }
        return new ArrayList<>(messages.subList(messages.size() - maxSize, messages.size()));
    }

    /**
     * 当前配置的历史轮数
     */
    public int getMaxHistoryRounds() {
        Integer rounds = agentProperties.getChat().getMaxHistoryRounds();
        return rounds == null || rounds <= 0 ? 10 : rounds;
    }
}
