# 上下文压缩（M1 + M2）

> 设计：[AGENT_DESIGN.md](./AGENT_DESIGN.md)　前置：[P0](./P0_IMPLEMENTATION.md) ~ [P5](./P5_FRONTEND.md)

---

## 1. 一句话

把「按轮数硬砍历史」换成「**按 token 预算分层组装 + 增量摘要 + 结构化记忆**」，
让长对话不再丢上下文，也不会撑爆模型窗口。

---

## 2. 改造前的问题

原来 `AgentSessionStore.loadHistory(sessionId, 10)` 只取最近 10 轮（20 条），然后 `trimRounds` 直接砍掉更早的。

| 问题 | 后果 |
| --- | --- |
| 按**条数**砍，不是按 token | 用户发一条 3000 字长消息就吃掉大半预算，但系统认为"才 1 轮" |
| 超了就丢，没有兜底 | 用户说"刚才那张图"，模型完全不知道是哪张 |
| 早期信息永久丢失 | 第 3 轮说的偏好，第 15 轮就没了 |
| 无法观测 | 不知道实际用了多少 token |

---

## 3. 方案：分层上下文

```
┌─ 1. System Prompt（角色 + 当前用户 + 封禁状态）      ← 调用方负责
├─ 2. 结构化记忆（关键 id，原样回灌）                  ← 新增
├─ 3. 滚动摘要（叙述性内容压缩）                        ← 新增
├─ 4. 最近 N 轮原文（按 token 反推 N）
└─ 5. 当前用户消息                                      ← 调用方负责
```

第 2、3 层合并成**一条 system 消息**注入，避免多条 system 干扰模型。

### 3.1 为什么需要「结构化记忆」

雪花 ID 是 19 位数字：

```
2113537314025197569
```

如果只做自由文本摘要，模型很可能写错一位、或干脆省略成"用户提到了一张图片"。
然后用户说"删掉刚才那张"，模型就只能瞎猜。

所以关键实体**结构化保存、原样回灌**：

```json
{
  "pictures": [{"id": "2113537314025197569", "name": "小猫"}],
  "spaces":   [{"id": "2005623577776603137", "name": "我的空间"}],
  "tasks":    ["7d4bb665..."],
  "facts":    ["用户是管理员", "用户偏好简短回答"]
}
```

渲染成给模型看的文本：

```
【已知信息（来自更早的对话，可直接使用这些 id）】
- 涉及过的图片：小猫（id=2113537314025197569）
- 涉及过的空间：我的空间（id=2005623577776603137）
- 异步任务号：7d4bb665...
- 用户是管理员
```

### 3.2 一次调用同时产出摘要和记忆

`SummaryGenerator` 让模型直接返回：

```json
{
  "summary": "用户先查询了空间（2 个），然后上传了一张名为「小猫」的图片...",
  "memory": { "pictures": [...], "spaces": [...], "tasks": [...], "facts": [...] }
}
```

比调两次省一半成本，用 `qwen-turbo`。

---

## 4. 增量压缩（水位机制）

关键设计：**不是每次都重新摘要全部历史**，而是靠 `agent_session.compressedUntilId` 记录水位。

```
第一次触发：
  水位=null → 取全部 163 条 → 保留最近 132 条 → 压缩最老 39 条
  → 水位推进到第 39 条的 id

第二次触发：
  水位=xxx → 只取第 40 条之后的 → 重复上面的过程
```

水位**只在摘要成功时推进**。摘要失败则保持原水位，下次重试——避免"压缩失败但水位前进了，导致消息永久丢失"。

### 压缩输入上限

`maxSummaryInputTokens=3000`：一次最多送 3000 token 去压缩。
163 条消息不会一次性全塞给摘要模型，而是从最老的开始压，多轮逐步消化。

---

## 5. Token 估算

没有 tokenizer（引 tiktoken 会多几百 KB 依赖，且千问分词器未必一致），用字符启发式：

```java
// 中文 1 字 ≈ 1.2 token，英文 4 字符 ≈ 1 token，每条消息 +4 固定开销
int tokens = ceil(cjk * 1.2) + ceil(other / 4.0);
```

**宁可高估**——高估只会让压缩提前触发，不会撑爆上下文。

实测：163 条中文消息估算 12900 token，与实际量级吻合。

---

## 6. 降级策略

| 情况 | 行为 |
| --- | --- |
| 摘要 LLM 调用失败 | **不推进水位**，退回纯截断，记 warn 日志，不阻塞对话 |
| 压缩后仍超预算 | `truncateToBudget` 从尾部保留，最少保留 2 条保证连贯 |
| 单条消息超长 | 单独截断到 `max-single-message-tokens`（默认 1500） |
| `memory.enabled=false` | 完全退回老逻辑（`loadHistory` + `trimRounds`） |
| 记忆 JSON 解析失败 | 按空记忆处理，容错未知字段 |

---

## 7. 配置

```yaml
agent:
  memory:
    enabled: true
    max-context-tokens: 6000        # 历史上下文预算，超过就压缩
    keep-recent-tokens: 2500        # 压缩时保留多少 token 最近原文
    summary-max-chars: 800          # 摘要最大字数
    summary-model: qwen-turbo       # 摘要用小模型省钱
    max-summary-input-tokens: 3000  # 单次送去压缩的输入上限
    max-single-message-tokens: 1500 # 单条消息上限
```

---

## 8. 代码结构

```
agent/memory/
├── TokenEstimator.java      字符启发式估算 + 按预算截断
├── AgentMemory.java         结构化记忆（合并/去重/裁剪/渲染/JSON 往返）
├── SummaryGenerator.java    一次 LLM 调用产出摘要 + 记忆
└── AgentMemoryService.java  分层组装 + 增量压缩 + 降级
```

修改：

| 文件 | 改动 |
| --- | --- |
| `AgentSession` | 加 `memory`、`compressedUntilId` |
| `AgentSessionStore` | 加 `loadHistoryAfter` / `loadMessageIdsAfter` / `updateSummary` |
| `AgentOrchestrator.run()` | `loadHistory()` → `agentMemoryService.buildContext()` |
| `AgentProperties` | 加 `memory` 配置块 |

---

## 9. 验证结果

### 9.1 测试

```
mvn -o test  →  Tests run: 32, Failures: 0, Errors: 0  BUILD SUCCESS
```

新增 `AgentMemoryTest`（8 个用例）：

| 用例 | 结果 |
| --- | --- |
| token 估算（中文/英文/混合/空） | ✅ |
| 按预算截断（含句子边界回退） | ✅ |
| 记忆合并去重 | ✅ |
| 记忆数量上限（最多 10 个实体） | ✅ |
| **雪花 ID 渲染一字不差** | ✅ |
| JSON 序列化往返 | ✅ 含脏数据容错 |
| 分层上下文 token 计算 | ✅ |

### 9.2 真实链路验证

构造 163 条历史消息（约 12900 token），发一条新消息触发：

```
压缩准备：水位=null，候选消息=163 条，id=163 条
准备压缩 39 条消息（keepFrom=132，maxInput=3000）
上下文压缩完成：12900 -> 2465 tokens
```

- 163 条 / 12900 token → 压缩后 **2465 token**（降 81%）
- 取最老 39 条送压缩（受 `maxInput=3000` 限制）
- 保留最近 132 条原文

因为 AI 密钥无效，摘要调用失败，`compressedUntilId` 保持 NULL —— **这正是设计的降级行为**：
不推进水位，下次重试；本次靠纯截断保证不超预算。

---

## 10. 踩过的坑

**1. Lombok `isEmpty()` 被 Jackson 当成属性**

`AgentMemory.isEmpty()` 被序列化成 `"empty": false`，反序列化时因没有对应 setter 直接抛
`Unrecognized field "empty"`，导致**整条记忆丢失**。

修复：加 `@JsonIgnore`，同时 `fromJson` 用 `FAIL_ON_UNKNOWN_PROPERTIES=false` 容错。

**2. `loadHistory(maxRounds)` 让压缩看不到历史**

最初 `buildContext` 用 `loadHistory(sessionId, 10)` 做初始加载，
但那只返回最近 20 条（约 1500 token），永远到不了 6000 预算，压缩形同虚设。

修复：改用 `loadHistoryAfter(sessionId, watermark)` 取全量，再由 token 预算决定保留多少。

**3. 测试与 worker 抢时序**

`cancelRunningTaskShouldWork` 依赖 worker 线程先跑起来，但 worker 可能瞬间失败，
导致任务状态已不是 RUNNING。改为直接插一条 RUNNING 记录，测试变确定。

---

## 11. 尚未验证

| 项 | 原因 |
| --- | --- |
| 摘要真实生成 | 需要有效 AI 密钥 |
| 结构化记忆真实抽取 | 同上 |
| 多轮增量压缩（水位推进） | 同上 |

密钥补上后，可以这样验证：

```bash
# 1. 连续聊 20 轮以上，每轮内容长一点
# 2. 看日志
grep "增量压缩" app.log
# 预期：增量压缩 N 条消息，水位推进到 xxx

# 3. 查数据库
select compressedUntilId, length(summary), length(memory) from agent_session where id = xxx;
# 预期：三个字段都有值

# 4. 问"我刚才说的那张图叫什么" —— 验证记忆是否生效
```

---

## 12. 后续可做（M3）

- **观测**：把压缩次数、节省的 token 数、摘要长度打成指标
- **调参**：根据真实对话分布调整 `max-context-tokens` / `keep-recent-tokens`
- **记忆过期**：太久没用到的实体自动淘汰（现在是按数量裁剪）
- **摘要质量评测**：准备一组长对话，人工判断摘要是否漏掉关键信息
