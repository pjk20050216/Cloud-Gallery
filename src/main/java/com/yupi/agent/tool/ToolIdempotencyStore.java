package com.yupi.agent.tool;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.yupi.agent.config.AgentProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * 工具执行幂等。
 * <p>
 * 用 toolCallId 做键：SSE 重连、前端重试、确认后重放都不会导致重复上传/重复删除。
 */
@Slf4j
@Component
public class ToolIdempotencyStore {

    private static final String KEY_PREFIX = "agent:tool:exec:";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private AgentProperties agentProperties;

    /**
     * 占位。返回 true 表示首次执行；false 表示已执行过。
     */
    public boolean tryLock(String toolCallId) {
        if (StrUtil.isBlank(toolCallId)) {
            return true;
        }
        try {
            Boolean ok = stringRedisTemplate.opsForValue()
                    .setIfAbsent(KEY_PREFIX + toolCallId, "RUNNING", ttlSeconds(), TimeUnit.SECONDS);
            return Boolean.TRUE.equals(ok);
        } catch (Exception e) {
            log.warn("幂等占位失败，按首次执行处理：{}", e.getMessage());
            return true;
        }
    }

    public ToolResult getCached(String toolCallId) {
        if (StrUtil.isBlank(toolCallId)) {
            return null;
        }
        try {
            String json = stringRedisTemplate.opsForValue().get(KEY_PREFIX + toolCallId);
            if (StrUtil.isBlank(json) || "RUNNING".equals(json)) {
                return null;
            }
            return JSONUtil.toBean(json, ToolResult.class);
        } catch (Exception e) {
            return null;
        }
    }

    public void cache(String toolCallId, ToolResult result) {
        if (StrUtil.isBlank(toolCallId) || result == null) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(KEY_PREFIX + toolCallId,
                    JSONUtil.toJsonStr(result), ttlSeconds(), TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("幂等结果缓存失败：{}", e.getMessage());
        }
    }

    public void release(String toolCallId) {
        if (StrUtil.isBlank(toolCallId)) {
            return;
        }
        try {
            stringRedisTemplate.delete(KEY_PREFIX + toolCallId);
        } catch (Exception e) {
            log.warn("幂等占位释放失败：{}", e.getMessage());
        }
    }

    private long ttlSeconds() {
        Integer ttl = agentProperties.getConfirm().getIdempotencyTtlSeconds();
        return ttl == null || ttl <= 0 ? 600 : ttl;
    }
}
