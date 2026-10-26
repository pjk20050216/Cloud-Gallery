# 智能体 P3 实现说明（异步智能体）

> 前置：[P0](./P0_IMPLEMENTATION.md)、[P1](./P1_IMPLEMENTATION.md)、[P2](./P2_IMPLEMENTATION.md)
> 设计：[AGENT_DESIGN.md](./AGENT_DESIGN.md)　代码基线：Spring Boot 2.7.6 / Java 11

---

## 1. 一句话

把「用户得一直等着」的同步对话，扩展成**可提交、可轮询、可取消**的异步任务；
并让长耗时操作（批量抓图）以「立即返回 taskId + 后台跑 + 进度可查」的方式接入智能体。

---

## 2. 三类异步，各司其职

| 类型 | 场景 | 实现 |
| --- | --- | --- |
| **流式** | 普通对话，边生成边显示 | SSE（P0 已有） |
| **异步任务** | 用户不想等（批量操作、长对话） | `agent_task` 表 + 线程池 + 轮询 |
| **长耗时工具** | 工具本身要跑很久 | 工具立即返回 `taskId`，再调 `task.getStatus` 轮询 |

---

## 3. 任务生命周期

```
提交 → PENDING（排队中，progress=0）
  → RUNNING（progress=1，"执行中"）
  → SUCCESS（progress=100，outputJson 存结果）
  / FAILED（errorMsg 记录真实原因）
  / CANCELED（用户主动取消）
```

关键设计：

- **失败不吞异常**。worker 捕获所有异常写入 `errorMsg`，用户能看到「AI 密钥无效或已过期」这种真实原因，而不是一句「系统错误」。
- **取消是协作式的**。`cancel()` 只把状态置为 `CANCELED`；worker 在循环里用 `isCanceled()` 轮询，发现后停止并**丢弃结果**（不会把已取消的任务改回 SUCCESS）。
- **并发限制用 Redis SETNX + TTL**，不是 Redisson 信号量。理由：进程崩溃时 TTL 自动释放，用户不会被永久锁死。默认每用户 1 个并发、锁 900 秒。

---

## 4. 身份快照跨线程传递

这是 P3 最容易踩的坑：异步线程里没有 `HttpServletRequest`、没有 Sa-Token 上下文。

做法：Controller 线程构建 `AgentContext`，序列化后塞进 `agent_task.inputJson` 的 `__context` 字段，
worker 反序列化还原。

```java
// 提交时
Map<String, Object> input = AgentTaskWorker.withContext(context, payload);
agentTaskService.submit(userId, sessionId, type, input);

// 执行时
AgentContext context = readContext(task);   // Jackson 反序列化
```

⚠️ 为此把 `AgentContext` 从「`final` 字段 + `@Builder`」改成了「普通字段 + 无参构造」，
否则 Jackson 会报 `no Creators, like default constructor, exist`。
这个错误在第一次跑异步任务时就真实出现了，属于设计遗漏，已修正。

---

## 5. 长耗时工具

| 工具 | 风险 | 行为 |
| --- | --- | --- |
| `picture.batchUpload` | WRITE（仅管理员） | 立即创建 `BATCH_UPLOAD` 任务并返回 `taskId`，**不阻塞对话** |
| `task.getStatus` | READ_ONLY | 查询任务状态/进度/错误 |

典型对话：

```
用户：帮我批量下载 20 张猫咪的图片
AI  ：[调用 picture.batchUpload] → 已提交任务 7d4bb665...，共 20 张
用户：刚才那个任务好了吗
AI  ：[调用 task.getStatus] → 任务已完成，成功上传 18 张
```

---

## 6. 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/agent/chat/async` | 提交异步对话，立即返回 taskId |
| GET | `/api/agent/task/{taskId}` | 查询状态（含 output） |
| GET | `/api/agent/task/list` | 我的任务列表 |
| POST | `/api/agent/task/{taskId}/cancel` | 取消 |

响应示例：

```json
{
  "taskId": "d9730905166442af8e249a2448c23a36",
  "type": "CHAT",
  "status": "FAILED",
  "progress": 10,
  "progressMessage": "执行失败",
  "errorMsg": "AI 密钥无效或已过期，请检查配置",
  "startTime": "2026-10-23T07:39:14.000+00:00",
  "endTime": "2026-10-23T07:39:14.000+00:00"
}
```

---

## 7. 配置

```yaml
agent:
  task:
    user-concurrency: 1        # 每用户同时只能跑一个任务
    lock-ttl-seconds: 900      # 并发锁 TTL，进程崩溃后自愈
    timeout-seconds: 300       # 单任务最长执行时间
    batch-upload-max-count: 30 # 批量抓取上限
    list-limit: 20
```

---

## 8. 验证结果

### 8.1 测试

```
mvn -o test  →  Tests run: 17, Failures: 0, Errors: 0  BUILD SUCCESS
```

| 测试类 | 用例数 | 覆盖 |
| --- | --- | --- |
| `AgentToolIntegrationTest` | 8 | 工具注册、权限、必填、高危确认、真实查询 |
| `AgentRoutingTest` | 5 | 多智能体注册、规则路由、工具白名单隔离 |
| `AgentTaskTest` | 3 | 状态流转、取消、越权隔离 |
| `Project2025122CloudGalleryApplicationTests` | 1 | 上下文加载 |

`AgentTaskTest` 关键断言：

| 用例 | 结果 |
| --- | --- |
| 提交 → RUNNING → 进度 50 → SUCCESS(100%) | ✅ |
| 取消 RUNNING 任务 | ✅ 状态变 CANCELED，`isCanceled` 为 true |
| 重复取消已结束任务 | ✅ 被正确拒绝 |
| 其他用户查询该任务 | ✅ 返回 null（越权隔离） |

### 8.2 真实 HTTP 验证

| 用例 | 结果 |
| --- | --- |
| 提交异步对话 | ✅ `PENDING`，返回 taskId |
| 轮询任务 | ✅ `FAILED`，`progress=10`，`errorMsg="AI 密钥无效或已过期，请检查配置"` |
| 连续提交两次 | ✅ 第二次被拒：`你还有任务正在执行，请等它完成或先取消` |
| 任务列表 | ✅ 按时间倒序返回 |
| 取消已结束任务 | ✅ `任务不存在或已结束，无法取消` |
| 取消后重新提交 | ✅ 锁已释放，可正常提交 |

### 8.3 尚未验证

| 项 | 原因 |
| --- | --- |
| `BATCH_UPLOAD` 真实执行 | 依赖 COS（桶已删除）和 Bing 图片抓取 |
| 异步对话的成功路径 | 依赖有效 AI 密钥 |
| 任务超时自动失败 | 需要构造 >300s 的阻塞场景 |

但**失败路径已完整验证**：外部依赖不可用时，任务被正确标记为 `FAILED` 并保留真实错误信息。

---

## 9. 顺带的改动

| 文件 | 改动 | 原因 |
| --- | --- | --- |
| `PictureService` / `PictureServiceImpl` | `uploadPictureByBatch` 增加带 `Consumer<Integer> onProgress` 的重载；返回**真实成功数**而非 limit | 异步任务需要进度；原实现返回的是「处理数量」不是「成功数量」，会误导 |
| `AgentContext` | `final` 字段 → 普通字段 + `@NoArgsConstructor` | 支持 JSON 序列化往返 |
| `PictureAgent` / `GeneralAgent` | 工具白名单加入 `picture.batchUpload` / `task.getStatus` | P3 新工具接入 |

---

## 10. 下一步（P4 封禁体系）

P3 完成后，设计文档里剩下的最后一块：

- `user_ban_record` 表 + `user.userStatus`
- `UserBanService` / 管理端封禁解封接口 / 登录拦截
- `account.myBanRecords`、`account.submitAppeal` 工具
- 被封用户只读放行（`ToolGuard` 闸 1 的钩子已留好）

这一块能直接兑现最初需求里「用户被封了，问 AI 为什么被封」的场景。
