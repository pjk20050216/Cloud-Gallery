# 智能体 P1 实现说明（写能力 + 附件 + 安全护栏）

> 前置：[P0_IMPLEMENTATION.md](./P0_IMPLEMENTATION.md)　设计：[AGENT_DESIGN.md](./AGENT_DESIGN.md)
> 代码基线：Spring Boot 2.7.6 / Java 11　模型：阿里云百炼千问（qwen-plus）

---

## 1. 一句话

在 P0 的只读闭环上，补上了**附件上传、写操作工具、四道安全闸、二次确认、幂等、审计**，
并且修掉了会让这些能力直接失效的几处既有 bug（数据源、公共图库、schema）。

---

## 2. 新增能力

### 2.1 工具清单（8 个）

| 工具 | 风险 | 权限 | 说明 |
| --- | --- | --- | --- |
| `help.listCapabilities` | READ_ONLY | — | 能力清单 |
| `picture.search` | READ_ONLY | — | 按关键词/分类/标签搜索自己的图片 |
| `picture.getDetail` | READ_ONLY | `picture:view` | 单张图片详情 |
| `picture.uploadByUrl` | WRITE | `picture:upload` | 把网络图片存进图库 |
| `picture.uploadFromAttachment` | WRITE | `picture:upload` | 把对话中上传的图片存进图库 |
| `picture.edit` | WRITE | `picture:edit` | 改名称/简介/分类/标签 |
| `picture.delete` | **DANGEROUS** | `picture:delete` | 删除图片（需二次确认） |
| `space.listMine` | READ_ONLY | — | 空间列表与额度 |

### 2.2 四道安全闸（`ToolGuard`）

```
1. 封禁校验   （P4 接入 userStatus，已留钩子）
2. 权限校验   context.hasPermission(tool.requiredPermission())
3. 参数必填   按工具 JSON Schema 的 required 校验
4. 高危确认   DANGEROUS 且未确认 → 抛 ConfirmRequiredException
```

所有判断都基于 `AgentContext` 快照，**不用 Sa-Token 的 ThreadLocal**（异步线程取不到）。

### 2.3 二次确认流程

```
LLM 调 picture.delete
  → ToolGuard 抛 ConfirmRequiredException（附确认文案）
  → 编排器把"推理现场"存进 Redis（PendingTurn：完整 messages + 待执行调用）
  → SSE 推 event: confirm_required { tool, args, summary, message=<confirmToken> }
  → 前端弹窗
  → POST /api/agent/confirm {confirmToken, approve:true}
  → 编排器取出 PendingTurn，执行工具，把结果回灌，继续 ReAct 循环
  → 返回最终回答
```

关键点：**不是从头重跑**，而是从断点续跑，所以之前的上下文和 token 都不会浪费。

拒绝时（`approve:false`）会写入一条"用户取消了该操作"的 tool 消息，模型据此给出得体回复。

### 2.4 幂等

`ToolIdempotencyStore`：键为 `toolCallId`，`SETNX` 占位 → 执行 → 缓存结果。
- 成功：缓存结果，重复调用直接返回缓存
- 失败：释放占位，允许重试

覆盖 SSE 重连、前端重试、确认后重放三种场景，避免重复上传/重复删除。

### 2.5 审计

每次工具调用写 `agent_tool_audit`：traceId、会话、用户、工具名、风险等级、入参、出参摘要、耗时、成功与否。
写库失败不影响主流程。

### 2.6 附件

```
POST /api/agent/attachment/upload  (multipart)
  → 校验 10MB / jpeg|png|jpg|webp
  → 上传到 COS 临时目录 agent/temp/{userId}/...
  → 落 agent_attachment，返回 {attachmentId, url, fileName, fileSize}
```

入图库时（`picture.uploadFromAttachment`）：
```
按 attachmentId 取附件（校验归属）
  → 从 COS 下载到本地临时文件
  → 包装成 MultipartFile（FileMultipartFile）
  → 走 pictureService.uploadPicture(multipartFile, ...)   ← 与普通上传完全同一条链路
  → 标记 CONSUMED + 删除 COS 临时对象
```

**为什么不直接用 URL 上传？** `UrlPictureUpload.validPicture` 限制 2MB，而附件上限是 10MB；
包装成 MultipartFile 可以复用 10MB + 后缀白名单 + 图片处理 + 审核参数 + 空间计数等全部逻辑。

---

## 3. 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/agent/chat` | 非流式对话 |
| POST | `/api/agent/chat/stream` | SSE 流式对话 |
| POST | `/api/agent/confirm` | 高危操作确认（非流式） |
| POST | `/api/agent/confirm/stream` | 高危操作确认（SSE） |
| POST | `/api/agent/attachment/upload` | 上传附件 |
| GET | `/api/agent/session/list` | 会话列表 |
| GET | `/api/agent/session/{id}/messages` | 历史消息 |
| DELETE | `/api/agent/session/{id}` | 删除会话 |

### SSE 事件

| type | 字段 | 说明 |
| --- | --- | --- |
| `agent_switch` | agent, displayName | 选中智能体 |
| `session` | sessionId | 会话建立 |
| `tool_call` | tool, toolCallId, args | 即将调工具 |
| `tool_result` | tool, toolCallId, success, summary | 工具结果 |
| `confirm_required` | tool, args, summary, message(=token) | **需用户确认** |
| `delta` | content | 文本增量 |
| `done` | sessionId, messageId, promptTokens, completionTokens | 本轮结束 |
| `error` | code, message | 出错 |

---

## 4. 数据模型

新增两张表（`sql/agent.sql`）：

- `agent_attachment`：附件（url、cosKey、fileName、fileSize、status）
- `agent_tool_audit`：工具调用审计

配置新增：

```yaml
agent:
  confirm:
    token-ttl-seconds: 300
    idempotency-ttl-seconds: 600
    pending-turn-ttl-seconds: 600
```

---

## 5. ⚠️ 本轮又发现的既有阻塞（都已修复）

P1 的写操作需要真正读写数据库，于是又暴露了几个**更底层**的问题：

| # | 问题 | 影响 | 处理 |
| --- | --- | --- | --- |
| 1 | **数据源根本没生效**：配置写在 `spring.shardingsphere.datasource` 下，而 `ShardingSphereAutoConfiguration` 在启动类里被 exclude 了 | Spring Boot 退化成**内嵌 H2 内存空库**，所有 SQL 都报"表不存在"。也就是说项目此前所有涉及数据库的功能都是坏的 | 在 `application.yml` 补 `spring.datasource`（MySQL） |
| 2 | `space_user` 表缺 `totalTokens/usedTokens/remainingTokens` 三列，但 `SpaceUser` 实体有 | 任何 `spaceUserService.lambdaQuery()` 都会抛 `Unknown column`，团队空间、`SpaceUserAuthManager` 团队分支全挂 | 补列（已执行） |
| 3 | 公共图库上传/删除必失败：`eq(Space::getId, null)` 更新 0 行后抛"空间更新失败" | 用户往公共图库传图、删图都失败 | `PictureServiceImpl` 加 `spaceId != null` 判断 |
| 4 | `application-local.yml` 的键名 `aliyunai.apikey` 与代码里的 `aliYunOssAi.apiKey` 不一致 | 密钥读不到，AI 功能静默失效 | 统一为 `aliYunOssAi.apiKey`，并新增 `agent.llm.api-key` |
| 5 | 没有激活任何 profile | `application-local.yml` 从未被加载，COS/AI 配置全空 | `spring.profiles.active: ${SPRING_PROFILES_ACTIVE:local}` |

> 第 1 条最要命：**在此之前，这个项目连不上 MySQL**。所有"看起来能跑"的接口其实都没碰数据库。

---

## 6. 验证结果

### 6.1 编译与单测

```
mvn -o -DskipTests compile          → BUILD SUCCESS
mvn -o test -Dtest=AgentToolIntegrationTest → Tests run: 8, Failures: 0, Errors: 0
```

单测覆盖（不依赖 LLM）：

| 用例 | 结果 |
| --- | --- |
| 工具全部注册（8 个） | ✅ 含 `picture.delete` 为 DANGEROUS |
| 高危工具需确认 | ✅ 抛 `ConfirmRequiredException`，文案含"删除/不可恢复" |
| 必填参数校验 | ✅ 缺 `url` 时抛 `IllegalArgumentException` |
| 权限拦截 | ✅ 无 `picture:edit` 权限时被拦 |
| `picture.search` 真实查询 | ✅ success=true，返回 `[]`（测试用户无图） |
| `space.listMine` 真实查询 | ✅ success=true，"当前用户还没有创建任何空间" |
| `help.listCapabilities` | ✅ 返回 7 项能力 |

### 6.2 启动

```
ToolRegistry : 智能体工具注册完成，共 8 个：
  [help.listCapabilities, picture.delete, picture.edit, picture.getDetail,
   picture.search, picture.uploadByUrl, picture.uploadFromAttachment, space.listMine]
Started Project2025122CloudGalleryApplication in 7.295 seconds
```

### 6.3 接口（真实登录后）

| 用例 | 结果 |
| --- | --- |
| 登录 `agenttest` | ✅ code=0 |
| `/agent/chat` 无有效密钥 | ✅ `{"code":50001,"message":"AI 密钥无效或已过期，请检查配置"}` |
| `/agent/attachment/upload` 传 `pom.xml` | ✅ `{"code":40000,"message":"仅支持 jpeg/png/jpg/webp 格式"}` |
| `/agent/confirm` 无效令牌 | ✅ `{"code":40000,"message":"确认已过期，请重新发起请求"}` |
| `/agent/session/list` | ✅ 返回会话，已落库 |
| `DELETE /agent/session/{id}` | ✅ `{"code":0,"data":true}` |

### 6.4 尚未验证（依赖你的环境）

| 项 | 卡在哪 |
| --- | --- |
| **真实对话闭环** | 当时配置的 AI 密钥已失效（返回 `AI 密钥无效或已过期`） |
| **附件真正上传到 COS** | 当时使用的 COS 桶已被删除：`NoSuchBucket (404)` |
| **图片入图库 / 编辑 / 删除** | 依赖 COS 可用（上传要写对象存储） |

所以写操作工具目前只验证到「参数校验 + 权限 + 幂等 + 审计」这一层，
**落库与对象存储那一步需要你补上有效的 COS 桶和 AI 密钥**。

---

## 7. 你需要做的三件事

1. **填 AI 密钥**（你说自己填）——`src/main/resources/application-local.yml`：

```yaml
aliYunOssAi:
  apiKey: sk-你的真实密钥
# 或
agent:
  llm:
    api-key: sk-你的真实密钥
```

2. **配置一个可用的 COS 桶**——在 `application-local.yml` 里填 `cos.client.bucket / secretId / secretKey / host`。

3. **配置 MySQL 密码**——在 `application-local.yml` 里填 `spring.datasource.password`。
   该文件已被 `.gitignore` 忽略，不会进仓库。

> ⚠️ 不要把密钥、密码写进 `application.yml` 或任何提交到仓库的文件，
> 一律通过环境变量或 `application-local.yml` 提供。

---

## 8. 自测脚本

```bash
# 1. 登录
curl -c cookie.txt -X POST http://localhost:8082/api/user/login \
  -H 'Content-Type: application/json' \
  -d '{"userAccount":"agenttest","userPassword":"test12345"}'

# 2. 上传附件
curl -b cookie.txt -X POST http://localhost:8082/api/agent/attachment/upload \
  -F "file=@test.png;type=image/png"
# → {"code":0,"data":{"attachmentId":"...","url":"https://..."}}

# 3. 让 AI 存进图库
curl -b cookie.txt -X POST http://localhost:8082/api/agent/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"把附件 123 传到我的图库，名字叫小猫"}'

# 4. 删除（会返回 confirm_required）
curl -N -b cookie.txt -X POST http://localhost:8082/api/agent/chat/stream \
  -H 'Content-Type: application/json' \
  -d '{"message":"把「小猫」删掉"}'
# → event: confirm_required  data: {"message":"ct_xxx", ...}

# 5. 确认
curl -b cookie.txt -X POST http://localhost:8082/api/agent/confirm \
  -H 'Content-Type: application/json' \
  -d '{"confirmToken":"ct_xxx","approve":true}'
```

---

## 9. 与设计文档的偏差

| 设计 | 实现 | 原因 |
| --- | --- | --- |
| `ToolContext` 独立类 | 工具直接收 `AgentContext` | 少一层包装，P0 起就这么做的 |
| 确认令牌只存"要做什么" | 额外存了完整推理现场（`PendingTurn`） | 否则确认后要重跑整轮，浪费 token 且上下文不一致 |
| 附件存本地 | 存 COS + 消费时下载 | 跨重启/多实例更稳，且能直接给前端预览 |
| `picture.batchUpload` | 未做 | 属于 P3（异步任务） |
| `picture.outPainting` | 未做 | 属于 P3（长耗时工具） |

---

## 10. 下一步（P2 / P3）

**P2 多智能体**：`AgentRouter` 加规则 + LLM 分类，拆 `PictureAgent` / `SpaceAgent` / `AccountAgent`，
工具白名单隔离，`agent.handoff` 转交。

**P3 异步**：`agent_task` 表 + worker，支持批量上传、扩图轮询、进度查询。

**P4 封禁体系**：`user_ban_record` + `account.*` 工具，让被封用户能问"我为什么被封"。
