package com.yupi.agent.tool;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yupi.agent.config.AgentProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 高危操作的确认令牌。
 * <p>
 * 令牌里只存"要做什么"，真正的执行由编排器在用户确认后完成。
 */
@Slf4j
@Component
public class ConfirmTokenStore {

    private static final String KEY_PREFIX = "agent:confirm:";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private AgentProperties agentProperties;

    @Resource
    private ObjectMapper objectMapper;

    @Data
    public static class Payload {
        private Long userId;
        private Long sessionId;
        private String toolName;
        private Map<String, Object> args;
        private String preview;
    }

    public String create(Payload payload) {
        String token = "ct_" + IdUtil.fastSimpleUUID();
        try {
            stringRedisTemplate.opsForValue().set(KEY_PREFIX + token,
                    objectMapper.writeValueAsString(payload), ttlSeconds(), TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("创建确认令牌失败", e);
            throw new IllegalStateException("创建确认令牌失败");
        }
        return token;
    }

    public Payload get(String token) {
        if (StrUtil.isBlank(token)) {
            return null;
        }
        try {
            String json = stringRedisTemplate.opsForValue().get(KEY_PREFIX + token);
            if (StrUtil.isBlank(json)) {
                return null;
            }
            return objectMapper.readValue(json, Payload.class);
        } catch (Exception e) {
            log.warn("读取确认令牌失败：{}", e.getMessage());
            return null;
        }
    }

    public void remove(String token) {
        try {
            stringRedisTemplate.delete(KEY_PREFIX + token);
        } catch (Exception e) {
            log.warn("删除确认令牌失败：{}", e.getMessage());
        }
    }

    public long ttlSeconds() {
        Integer ttl = agentProperties.getConfirm().getTokenTtlSeconds();
        return ttl == null || ttl <= 0 ? 300 : ttl;
    }
}
