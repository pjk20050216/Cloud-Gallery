package com.yupi.agent.core;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yupi.agent.config.AgentProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * 待确认推理现场的存储
 */
@Slf4j
@Component
public class PendingTurnStore {

    private static final String KEY_PREFIX = "agent:pending-turn:";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private AgentProperties agentProperties;

    @Resource
    private ObjectMapper objectMapper;

    public void save(String confirmToken, PendingTurn turn) {
        try {
            stringRedisTemplate.opsForValue().set(KEY_PREFIX + confirmToken,
                    objectMapper.writeValueAsString(turn), ttlSeconds(), TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("保存待确认现场失败", e);
            throw new IllegalStateException("保存确认现场失败");
        }
    }

    public PendingTurn get(String confirmToken) {
        if (StrUtil.isBlank(confirmToken)) {
            return null;
        }
        try {
            String json = stringRedisTemplate.opsForValue().get(KEY_PREFIX + confirmToken);
            if (StrUtil.isBlank(json)) {
                return null;
            }
            return objectMapper.readValue(json, PendingTurn.class);
        } catch (Exception e) {
            log.warn("读取待确认现场失败：{}", e.getMessage());
            return null;
        }
    }

    public void remove(String confirmToken) {
        try {
            stringRedisTemplate.delete(KEY_PREFIX + confirmToken);
        } catch (Exception e) {
            log.warn("删除待确认现场失败：{}", e.getMessage());
        }
    }

    private long ttlSeconds() {
        Integer ttl = agentProperties.getConfirm().getPendingTurnTtlSeconds();
        return ttl == null || ttl <= 0 ? 600 : ttl;
    }
}
