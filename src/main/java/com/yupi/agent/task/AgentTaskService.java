package com.yupi.agent.task;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yupi.agent.config.AgentProperties;
import com.yupi.exception.BusinessException;
import com.yupi.exception.ErrorCode;
import com.yupi.mapper.AgentTaskMapper;
import com.yupi.model.entity.AgentTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * 异步任务服务：提交、查询、取消、进度更新。
 * <p>
 * 并发控制用 Redis 的 SETNX + TTL，而不是 Redisson 信号量：
 * 进程崩溃时 TTL 会自动释放，不会把用户永久锁死。
 */
@Slf4j
@Service
public class AgentTaskService {

    private static final String RUNNING_LOCK_PREFIX = "agent:task:running:";

    @Resource
    private AgentTaskMapper agentTaskMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private AgentProperties agentProperties;

    @Resource
    private ObjectMapper objectMapper;

    @Resource(name = "agentExecutor")
    private Executor agentExecutor;

    @Resource
    private AgentTaskWorker agentTaskWorker;

    /**
     * 提交任务。会做用户级并发限制。
     *
     * @param input 序列化后存进 inputJson
     */
    public AgentTask submit(Long userId, Long sessionId, AgentTaskTypeEnum type, Object input) {
        String lockKey = RUNNING_LOCK_PREFIX + userId;
        long ttl = lockTtlSeconds();
        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", ttl, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(acquired)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "你还有任务正在执行，请等它完成或先取消");
        }

        AgentTask task = new AgentTask();
        task.setTaskId(IdUtil.fastSimpleUUID());
        task.setUserId(userId);
        task.setSessionId(sessionId);
        task.setType(type.name());
        task.setStatus(AgentTaskStatusEnum.PENDING.name());
        task.setProgress(0);
        task.setProgressMessage("排队中");
        task.setInputJson(safeJson(input));
        task.setCreateTime(new Date());
        task.setUpdateTime(new Date());

        try {
            agentTaskMapper.insert(task);
        } catch (Exception e) {
            // 落库失败要释放锁，否则用户被锁死
            releaseLock(userId);
            throw e;
        }

        agentExecutor.execute(() -> {
            try {
                agentTaskWorker.run(task.getTaskId());
            } finally {
                releaseLock(userId);
            }
        });
        return task;
    }

    // ---------------------------------------------------------------- 查询

    public AgentTask getByTaskId(Long userId, String taskId) {
        if (StrUtil.isBlank(taskId)) {
            return null;
        }
        AgentTask task = agentTaskMapper.selectOne(new LambdaQueryWrapper<AgentTask>()
                .eq(AgentTask::getTaskId, taskId));
        if (task == null || !userId.equals(task.getUserId())) {
            return null;
        }
        return task;
    }

    public List<AgentTask> listByUser(Long userId, int limit) {
        return agentTaskMapper.selectList(new LambdaQueryWrapper<AgentTask>()
                .eq(AgentTask::getUserId, userId)
                .orderByDesc(AgentTask::getCreateTime)
                .last("limit " + Math.max(1, Math.min(limit, 100))));
    }

    // ---------------------------------------------------------------- 状态流转

    public void markRunning(String taskId) {
        update(taskId, task -> {
            task.setStatus(AgentTaskStatusEnum.RUNNING.name());
            task.setStartTime(new Date());
            task.setProgress(1);
            task.setProgressMessage("执行中");
        });
    }

    public void updateProgress(String taskId, int progress, String message) {
        update(taskId, task -> {
            task.setProgress(Math.max(0, Math.min(100, progress)));
            if (StrUtil.isNotBlank(message)) {
                task.setProgressMessage(StrUtil.maxLength(message, 250));
            }
        });
    }

    public void markSuccess(String taskId, Object output) {
        update(taskId, task -> {
            task.setStatus(AgentTaskStatusEnum.SUCCESS.name());
            task.setProgress(100);
            task.setProgressMessage("已完成");
            task.setOutputJson(safeJson(output));
            task.setEndTime(new Date());
        });
    }

    public void markFailed(String taskId, String errorMsg) {
        update(taskId, task -> {
            task.setStatus(AgentTaskStatusEnum.FAILED.name());
            task.setProgressMessage("执行失败");
            task.setErrorMsg(StrUtil.maxLength(errorMsg, 1000));
            task.setEndTime(new Date());
        });
    }

    public void markCanceled(String taskId) {
        update(taskId, task -> {
            task.setStatus(AgentTaskStatusEnum.CANCELED.name());
            task.setProgressMessage("已取消");
            task.setEndTime(new Date());
        });
    }

    /**
     * 用户主动取消
     */
    public boolean cancel(Long userId, String taskId) {
        AgentTask task = getByTaskId(userId, taskId);
        if (task == null) {
            return false;
        }
        if (!AgentTaskStatusEnum.isRunning(task.getStatus())) {
            return false;
        }
        markCanceled(taskId);
        releaseLock(userId);
        return true;
    }

    /**
     * worker 在循环里轮询，判断是否已被取消
     */
    public boolean isCanceled(String taskId) {
        AgentTask task = agentTaskMapper.selectOne(new LambdaQueryWrapper<AgentTask>()
                .eq(AgentTask::getTaskId, taskId)
                .select(AgentTask::getId, AgentTask::getStatus));
        return task != null && AgentTaskStatusEnum.CANCELED.name().equals(task.getStatus());
    }

    // ---------------------------------------------------------------- 内部

    private interface TaskUpdater {
        void apply(AgentTask task);
    }

    private void update(String taskId, TaskUpdater updater) {
        AgentTask current = agentTaskMapper.selectOne(new LambdaQueryWrapper<AgentTask>()
                .eq(AgentTask::getTaskId, taskId));
        if (current == null) {
            return;
        }
        AgentTask update = new AgentTask();
        update.setId(current.getId());
        updater.apply(update);
        update.setUpdateTime(new Date());
        agentTaskMapper.updateById(update);
    }

    private void releaseLock(Long userId) {
        try {
            stringRedisTemplate.delete(RUNNING_LOCK_PREFIX + userId);
        } catch (Exception e) {
            log.warn("释放任务并发锁失败：{}", e.getMessage());
        }
    }

    private long lockTtlSeconds() {
        Integer ttl = agentProperties.getTask().getLockTtlSeconds();
        return ttl == null || ttl <= 0 ? 900 : ttl;
    }

    private String safeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    public int batchUploadMaxCount() {
        Integer max = agentProperties.getTask().getBatchUploadMaxCount();
        return max == null || max <= 0 ? 30 : max;
    }
}
