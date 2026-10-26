# 智能体 P2 实现说明（多智能体）

> 前置：[P0_IMPLEMENTATION.md](./P0_IMPLEMENTATION.md)、[P1_IMPLEMENTATION.md](./P1_IMPLEMENTATION.md)
> 设计：[AGENT_DESIGN.md](./AGENT_DESIGN.md)　代码基线：Spring Boot 2.7.6 / Java 11

---

## 1. 一句话

把 P0/P1 的单一通用助手拆成 **1 个路由 + 4 个专家智能体**，
用「规则优先 + LLM 分类兜底」选人，用「工具白名单」做能力隔离，
再给一个 `agent.handoff` 让智能体之间能按需转交。

---

## 2. 智能体清单

| 智能体 | 展示名 | 职责 | 工具白名单 |
| --- | --- | --- | --- |
| `picture` | 图片管家 | 上传、检索、编辑、删除 | `picture.search`、`picture.getDetail`、`picture.uploadByUrl`、`picture.uploadFromAttachment`、`picture.edit`、`picture.delete`、`space.listMine`、`agent.handoff` |
| `space` | 空间管家 | 容量、用量、额度、统计 | `space.listMine`、`picture.search`、`agent.handoff` |
| `account` | 账号助手 | 账号状态、封禁、申诉、余额 | `account.myStatus`、`help.listCapabilities`、`agent.handoff` |
| `general` | 通用助手 | 兜底、闲聊、能力介绍 | `help.listCapabilities`、`picture.search`、`space.listMine`、`agent.handoff` |

工具总数 10 个（P1 的 8 个 + `agent.handoff` + `account.myStatus`）。

**为什么每个智能体只拿自己那部分工具？**
两个原因：一是省 token（工具定义会进 prompt），二是天然降低模型乱调工具的概率。
比如空间管家根本看不到 `picture.delete`，就不可能误删。

---

## 3. 路由：三级策略

```
用户输入
   │
   ├─ 1. 规则命中（0 次 LLM 调用）
   │     账号词：封禁/被封/封号/禁言/申诉/解封/账号/会员/额度/余额/token
   │     图片词：上传/传图/图库/图片/照片/找图/搜图/删除/删掉/改名/标签/扩图
   │     空间词：空间/容量/占用/配额/统计/成员
   │     优先级：账号 > 图片 > 空间
   │
   ├─ 2. LLM 分类（规则没命中时）
   │     小模型 qwen-turbo + max_tokens=32 + temperature=0
   │     输出 {"agent":"picture","confidence":0.9}
   │     confidence < 0.6 视为无法判断
   │     任何异常 → 返回 null，绝不影响主流程
   │
   └─ 3. 兜底 → general
```

**为什么账号优先级最高？** 「我账号为什么被封了」同时含「账号」和「封」，
如果不优先匹配账号，很容易被「图片」之类的词抢走。

**为什么规则在前？** 常见说法（上传、空间、封禁）用规则命中率很高，
省一次 LLM 调用就是省一份延迟和成本。只有说不清楚的话才走分类器。

---

## 4. Handoff（转交）

子智能体在发现自己处理不了时，可以调用虚拟工具 `agent.handoff`：

```json
{"to": "space", "reason": "用户问的是空间额度，不是图片"}
```

编排器拦截处理（不真正执行工具）：

1. 目标不存在 → 回灌「转交失败：不存在名为 X 的智能体」
2. 超过跳数上限 → 回灌「转交次数已达上限，请直接回答」
3. 目标就是自己 → 回灌「你已经在负责这个领域了」
4. 否则 → 切换 `agent`、重算工具白名单、发 `agent_switch` 事件、回灌成功

跳数上限默认 2（`agent.chat.max-handoff-hops`），防止两个智能体互相踢皮球。

---

## 5. 编排器改造

`AgentOrchestrator.runLoop` 里 `agent` 从「参数」变成「可变状态」：

```java
SubAgent agent = initialAgent;
List<ToolSpec> tools = toolRegistry.specsOf(agent.toolNames());
int handoffHops = 0;

for (int step = ...) {
    ChatResponse response = callLlm(agent, messages, tools, onDelta);
    for (ToolCall call : response.getToolCalls()) {
        if (ToolNames.HANDOFF.equals(call.getName())) {
            // 切换 agent + tools，回灌 tool 消息，continue
        }
        // 正常工具执行
    }
}
```

关键点：**转交不打断 ReAct 循环**。assistant 的 tool_calls 消息已经进上下文，
只需补一条 handoff 的 tool 结果，下一轮 LLM 就会用新智能体的角色和工具继续推理。

另外 `resume()`（高危确认后续跑）改为从 `PendingTurn.agentType` 恢复智能体，
不再硬编码 general —— 否则「图片管家发起的删除」确认后会被通用助手接管。

---

## 6. 接口

新增：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/agent/agents` | 列出 4 个智能体及其工具白名单（需登录） |

SSE 事件新增语义：

| type | 说明 |
| --- | --- |
| `agent_switch` | 首次路由 **或** 转交时都会发；转交时 `summary` 带转交原因 |

会话表 `agent_session.agentType` 会记录当前智能体，转交时同步更新。

---

## 7. 配置

```yaml
agent:
  llm:
    router-model: qwen-turbo   # 意图分类用
    default-model: qwen-plus   # 专家智能体用
  chat:
    enable-llm-router: true    # 规则没命中时是否用 LLM 分类
    max-handoff-hops: 2        # 单轮最多转交次数
```

---

## 8. 验证结果

### 8.1 编译与测试

```
mvn -o -DskipTests compile              → BUILD SUCCESS
mvn -o test -Dtest=AgentRoutingTest     → Tests run: 5, Failures: 0, Errors: 0
mvn -o test -Dtest=AgentToolIntegrationTest → Tests run: 8, Failures: 0
```

`AgentRoutingTest` 覆盖：

| 用例 | 结果 |
| --- | --- |
| 4 个智能体全部注册 | ✅ account / general / picture / space |
| 规则路由 | ✅ 7 条典型输入全部命中预期智能体 |
| 工具白名单隔离 | ✅ 空间管家无写操作、账号助手无图片删除 |
| 每个智能体引用的工具都存在 | ✅ 10 个工具全部可解析 |
| `account.myStatus` 不编造封禁记录 | ✅ `banned=false`、`banFeatureAvailable=false` |

### 8.2 启动

```
SubAgentRegistry : 智能体注册完成，共 4 个：[account, general, picture, space]
ToolRegistry     : 智能体工具注册完成，共 10 个：
  [account.myStatus, agent.handoff, help.listCapabilities, picture.delete, picture.edit,
   picture.getDetail, picture.search, picture.uploadByUrl, picture.uploadFromAttachment, space.listMine]
```

### 8.3 真实路由（SSE 实测）

| 用户输入 | 路由结果 | 事件序列 |
| --- | --- | --- |
| 我账号为什么被封了 | `account` 账号助手 | `agent_switch` → `session` → `error`(密钥无效) |
| 我有哪些空间 | `space` 空间管家 | 同上 |
| 帮我把这张图传到图库 | `picture` 图片管家 | 同上 |

路由发生在调 LLM 之前，所以即使密钥无效也能验证选人是否正确。

### 8.4 尚未验证

**完整的转交链路**（模型主动调 `agent.handoff`）和**分类器兜底**（规则没命中时走 LLM）
都需要有效密钥，当前密钥失效，无法实测。

`agent.handoff` 的编排器逻辑（目标校验、跳数限制、自我转交拒绝）已在代码里实现，
但只有模型真的发起调用才会触发。你补上密钥后可以用这句验证：

```
"我空间还剩多少额度"   → space（规则命中）
"你能帮我做点啥"       → 大概率 general，可能转交
```

---

## 9. 与设计文档的偏差

| 设计 | 实现 | 原因 |
| --- | --- | --- |
| 4 个智能体，AccountAgent 管封禁 | AccountAgent 已建，但封禁数据等 P4 | 封禁体系（`user_ban_record`）属于 P4；现在如实返回"未启用"，不编造 |
| 路由带 handoff 最多 2 跳 | ✅ 已实现 | — |
| 意图分类用 LLM | ✅ 已实现，且带置信度阈值与异常兜底 | — |
| 工具按域聚合成 `PictureTools` 类 | 一个工具一个 `@Component` | Spring 直接收集 `List<AgentTool>`，更简单 |

---

## 10. 下一步（P3 / P4）

**P3 异步智能体**：
- `agent_task` 表 + worker + 线程池
- `POST /agent/chat/async`、`GET /agent/task/{id}`、取消
- 长耗时工具：批量上传、AI 扩图轮询
- 每用户并发 1 个（Redisson 信号量）

**P4 封禁体系**：
- `user_ban_record` 表 + `user.userStatus`
- `UserBanService` / 管理端接口 / 登录拦截
- `account.myBanRecords`、`account.submitAppeal` 工具
- 被封用户只读放行（`ToolGuard` 闸 1 已留钩子）
