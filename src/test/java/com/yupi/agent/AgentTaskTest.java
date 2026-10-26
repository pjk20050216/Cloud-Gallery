package com.yupi.agent;

import com.yupi.agent.task.AgentTaskService;
import com.yupi.agent.task.AgentTaskStatusEnum;
import com.yupi.agent.task.AgentTaskTypeEnum;
import com.yupi.mapper.AgentTaskMapper;
import com.yupi.model.entity.AgentTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 异步任务框架测试：状态流转、取消、并发限制。
 * <p>
 * 不依赖 LLM/COS，也不依赖 worker 的执行时序——
 * 需要"运行中"状态时直接插一条 RUNNING 记录，避免与后台线程抢时序。
 * <p>
 * 运行：mvn -o test -Dtest=AgentTaskTest
 */
@SpringBootTest
class AgentTaskTest {

    private static final long USER_ID = 900000000000000002L;

    @Resource
    private AgentTaskService agentTaskService;

    @Resource
    private AgentTaskMapper agentTaskMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 每个用例结束都清掉并发锁，避免互相干扰（生产环境靠 TTL 自愈）
     */
    @AfterEach
    void clearLock() {
        stringRedisTemplate.delete("agent:task:running:" + USER_ID);
    }

    /**
     * 直接插入一条指定状态的任务，绕开 worker 的异步时序
     */
    private AgentTask insertTask(String status) {
        AgentTask task = new AgentTask();
        task.setTaskId(UUID.randomUUID().toString().replace("-", ""));
        task.setUserId(USER_ID);
        task.setType(AgentTaskTypeEnum.BATCH_UPLOAD.name());
        task.setStatus(status);
        task.setProgress(0);
        task.setProgressMessage("测试构造");
        task.setInputJson("{\"dummy\":true}");
        task.setCreateTime(new Date());
        task.setUpdateTime(new Date());
        if (!AgentTaskStatusEnum.PENDING.name().equals(status)) {
            task.setStartTime(new Date());
        }
        agentTaskMapper.insert(task);
        return task;
    }

    @Test
    void taskLifecycleShouldWork() {
        AgentTask task = insertTask(AgentTaskStatusEnum.PENDING.name());
        assertNotNull(task.getTaskId());
        System.out.println(">>> 构造任务 taskId=" + task.getTaskId() + " status=" + task.getStatus());

        AgentTask loaded = agentTaskService.getByTaskId(USER_ID, task.getTaskId());
        assertNotNull(loaded);
        assertEquals("PENDING", loaded.getStatus());

        // 推进到 RUNNING
        agentTaskService.markRunning(task.getTaskId());
        AgentTask running = agentTaskService.getByTaskId(USER_ID, task.getTaskId());
        assertEquals("RUNNING", running.getStatus());
        assertNotNull(running.getStartTime());
        System.out.println(">>> RUNNING, startTime=" + running.getStartTime());

        // 进度更新
        agentTaskService.updateProgress(task.getTaskId(), 50, "已处理一半");
        AgentTask half = agentTaskService.getByTaskId(USER_ID, task.getTaskId());
        assertEquals(50, half.getProgress());
        assertEquals("已处理一半", half.getProgressMessage());
        System.out.println(">>> 进度更新为 50%");

        // 成功收尾
        agentTaskService.markSuccess(task.getTaskId(), Map.of("result", "ok"));
        AgentTask done = agentTaskService.getByTaskId(USER_ID, task.getTaskId());
        assertEquals("SUCCESS", done.getStatus());
        assertEquals(100, done.getProgress());
        assertNotNull(done.getEndTime());
        System.out.println(">>> SUCCESS, output=" + done.getOutputJson());
    }

    @Test
    void cancelRunningTaskShouldWork() {
        AgentTask task = insertTask(AgentTaskStatusEnum.RUNNING.name());

        boolean canceled = agentTaskService.cancel(USER_ID, task.getTaskId());
        System.out.println(">>> 取消运行中任务：" + canceled);
        assertTrue(canceled);

        AgentTask canceledTask = agentTaskService.getByTaskId(USER_ID, task.getTaskId());
        assertEquals("CANCELED", canceledTask.getStatus());
        assertTrue(agentTaskService.isCanceled(task.getTaskId()));

        // 已结束的任务不能再次取消
        assertFalse(agentTaskService.cancel(USER_ID, task.getTaskId()));
        System.out.println(">>> 重复取消被正确拒绝");
    }

    @Test
    void finishedTaskShouldNotBeCancelable() {
        AgentTask task = insertTask(AgentTaskStatusEnum.SUCCESS.name());
        assertFalse(agentTaskService.cancel(USER_ID, task.getTaskId()));
        System.out.println(">>> 已完成任务不可取消");
    }

    @Test
    void otherUserShouldNotSeeTask() {
        AgentTask task = insertTask(AgentTaskStatusEnum.RUNNING.name());

        assertNull(agentTaskService.getByTaskId(USER_ID + 1, task.getTaskId()));
        assertFalse(agentTaskService.cancel(USER_ID + 1, task.getTaskId()));
        System.out.println(">>> 越权查询与取消都被正确拒绝");
    }

    @Test
    void concurrencyLockShouldRejectSecondSubmit() {
        // 手动占住锁，模拟"上一个任务还在跑"
        stringRedisTemplate.opsForValue().set("agent:task:running:" + USER_ID, "1", 60, java.util.concurrent.TimeUnit.SECONDS);

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("dummy", true);
        Exception ex = assertThrows(Exception.class,
                () -> agentTaskService.submit(USER_ID, null, AgentTaskTypeEnum.CHAT, input));
        System.out.println(">>> 并发限制生效：" + ex.getMessage());

        // 释放后可以正常提交
        stringRedisTemplate.delete("agent:task:running:" + USER_ID);
        AgentTask ok = agentTaskService.submit(USER_ID, null, AgentTaskTypeEnum.CHAT, input);
        assertNotNull(ok.getTaskId());
        System.out.println(">>> 释放锁后提交成功 taskId=" + ok.getTaskId());
        agentTaskService.cancel(USER_ID, ok.getTaskId());
    }
}
