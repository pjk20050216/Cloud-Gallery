# 智能体 P4 实现说明（封禁体系）

> 前置：[P0](./P0_IMPLEMENTATION.md)、[P1](./P1_IMPLEMENTATION.md)、[P2](./P2_IMPLEMENTATION.md)、[P3](./P3_IMPLEMENTATION.md)
> 设计：[AGENT_DESIGN.md](./AGENT_DESIGN.md)　代码基线：Spring Boot 2.7.6 / Java 11

---

## 1. 一句话

给平台补上**用户封禁体系**，并让智能体成为被封用户的自助通道：
能问清原因、能看到解封时间、能提交申诉，但做不了写操作。

这正好兑现最初需求里的场景：*"用户被封了，然后可以询问 AI 自己账号为什么被封"*。

---

## 2. 核心产品决策

> **被封禁的用户必须仍然能登录、并且能使用智能体。**

如果登录就被拒，用户连"我为什么被封"都问不了，体验是灾难性的。

所以采用「**登录放行 + 业务接口拦截 + 智能体只读**」三层设计：

```
被封用户请求
   │
   ├─ /user/login、/user/logout、/user/get/login  → 放行
   ├─ /agent/**                                   → 放行（ToolGuard 限制只读）
   ├─ /user/ban/my、/user/ban/appeal              → 放行（查记录、申诉）
   └─ 其余业务接口（/picture/**、/space/** ...）  → 拦截，返回 40300
```

---

## 3. 数据模型

`user_ban_record`：

| 字段 | 说明 |
| --- | --- |
| `banType` | 1-封禁 2-禁言 3-限制上传 |
| `reasonCode` | VIOLENT_CONTENT / SPAM / COPYRIGHT / OTHER |
| `reasonDetail` | 详细原因（**直接展示给用户**） |
| `relatedId` | 关联对象 id（如被举报图片） |
| `startTime` / `endTime` | 时间范围，`endTime` 为空 = 永久 |
| `status` | 0-生效中 1-已解封 2-已过期 |
| `appealStatus` | 0-未申诉 1-申诉中 2-通过 3-驳回 |
| `appealContent` / `appealReply` | 申诉内容与处理意见 |

**到期自动失效**：`getActiveBan` 发现 `endTime` 已过会顺手把状态改成「已过期」并返回 null，
不需要定时任务。

---

## 4. 拦截器

`UserBanInterceptor` 注册在 `WebMvcConfig`：

```java
registry.addInterceptor(userBanInterceptor)
        .addPathPatterns("/**")
        .excludePathPatterns("/user/login", "/user/logout", "/user/get/login",
                             "/agent/**", "/user/ban/my", "/user/ban/appeal", "/error");
```

⚠️ **路径模式是相对 context-path 的**。项目 context-path 是 `/api`，
所以必须写 `/**` 而不是 `/api/**`。

拦截时抛出 `BusinessException(FORBIDDEN_ERROR, ...)`，由 `GlobalExceptionHandler` 转成 JSON：

```json
{
  "code": 40300,
  "message": "账号已被封禁，暂时无法执行该操作。原因：上传了违规图片，涉及侵权内容。解封时间：2026-10-26 15:45。你可以通过智能体助手查询详情或提交申诉。"
}
```

错误信息里直接给出原因、解封时间，**并引导用户去智能体**——这是把拦截提示变成产品引导。

---

## 5. 智能体联动

### 5.1 身份快照带封禁状态

`AgentContext` 新增 `banned` + `banMessage`，在 `AgentContextFactory` 里查好
（异步线程查不了，必须在请求线程完成）。

### 5.2 系统提示词注入封禁状态

```
【账号状态：封禁中】账号当前处于封禁状态。原因：xxx。解封时间：xxx。申诉状态：未申诉。
该用户当前只能查询信息和提交申诉，写操作会被系统拒绝。
如果用户要求写操作，请如实说明限制，不要反复尝试调用写工具。
```

这样模型不会在封禁期间反复尝试调 `picture.delete` 然后一直失败。

### 5.3 ToolGuard 闸 1 正式生效

```java
if (context.isBanned()
        && tool.risk() != ToolRiskLevel.READ_ONLY
        && !tool.allowedWhenBanned()) {
    throw new IllegalStateException("账号处于封禁状态，暂时只能查询信息，不能执行该操作");
}
```

新增 `AgentTool.allowedWhenBanned()` 钩子，默认 `false`。
只有 `account.submitAppeal`（申诉）覆写为 `true`——否则申诉功能本身就没法用。

### 5.4 新增工具

| 工具 | 风险 | 封禁期可用 | 说明 |
| --- | --- | --- | --- |
| `account.myBanRecords` | READ_ONLY | ✅ | 查自己的全部处罚记录 |
| `account.submitAppeal` | WRITE | ✅（特例） | 提交申诉 |
| `account.myStatus` | READ_ONLY | ✅ | 已改为读取真实封禁数据 |

工具总数：**14 个**。

`AccountAgent` 的 system prompt 也相应加强：

```
- 只陈述工具返回的内容，绝不编造或推测处罚原因；
- 如果工具返回没有处罚记录，就明确告诉用户当前账号正常；
- 申诉前必须问清楚理由，不要替用户编理由；
- 封禁期间用户只能查询信息、提交申诉，不能上传/删除图片。
```

---

## 6. 接口

### 管理端

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/user/ban/add` | 封禁（`@AutoCheck(admin)`） |
| POST | `/api/user/ban/unban` | 解封 |
| POST | `/api/user/ban/appeal/review` | 审核申诉（通过即解封） |
| GET | `/api/user/ban/list` | 查询处罚记录 |

### 用户端

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/user/ban/my` | 查看自己的处罚记录（封禁期也放行） |
| POST | `/api/user/ban/appeal` | 提交申诉（封禁期也放行） |

---

## 7. 验证结果

### 7.1 测试

```
mvn -o test  →  Tests run: 22, Failures: 0, Errors: 0  BUILD SUCCESS
```

新增 `UserBanTest`（5 个用例）：

| 用例 | 结果 |
| --- | --- |
| 封禁 → 重复封禁被拒 → 解封 | ✅ |
| 提交申诉 → 重复申诉被拒 → 管理员驳回 | ✅ |
| 其他用户不能替人申诉 | ✅ |
| 封禁用户只能用只读工具，写工具被拦 | ✅ |
| 封禁用户仍可提交申诉（`allowedWhenBanned`） | ✅ |

### 7.2 真实 HTTP 端到端

用 admin 封禁 `agenttest2`（3 天，原因「上传了违规图片，涉及侵权内容」），然后：

| 步骤 | 结果 |
| --- | --- |
| 被封用户登录 | ✅ **成功**（关键：不能挡登录） |
| 访问 `/picture/list/page/vo` | ✅ 被拦，`40300` + 完整原因和解封时间 |
| 访问 `/user/ban/my` | ✅ 放行，返回处罚记录 |
| 访问 `/agent/chat/stream` | ✅ 放行，正确路由到 **account** 助手 |
| 提交申诉 | ✅ 成功，状态变「申诉中」 |
| 重复申诉 | ✅ 被拒「申诉已提交，请等待处理」 |
| 管理员封禁自己 | ✅ 被拒「不能封禁自己」 |

---

## 8. 仍未验证

| 项 | 原因 |
| --- | --- |
| AI 实际回答"你因为 X 被封" | 依赖有效 AI 密钥 |
| 申诉通过后自动解封 | 逻辑已实现（`reviewAppeal` 通过时同时置 `RELEASED`），但没有 HTTP 层实测 |

密钥补上后，用被封账号问「我账号为什么被封了」，预期 AI 会调 `account.myBanRecords`
并如实转述原因、解封时间、申诉状态。

---

## 9. 顺带发现的既有问题

**`SaTokenConfigure` 的拦截器路径写错了。**

```java
registry.addInterceptor(new SaInterceptor()).addPathPatterns("/api/**");
```

项目 `context-path` 是 `/api`，而拦截器路径是**相对 context-path** 的，
所以 `/api/**` 永远匹配不到——意味着所有 `@SaCheckLogin` / `@SaSpaceCheckPermission`
注解式鉴权实际上**从未生效**。

我没有直接改它，因为一旦改对，现有接口的鉴权行为会突然变化（可能暴露新的 403），
属于需要单独评估的改动。建议：

```java
registry.addInterceptor(new SaInterceptor()).addPathPatterns("/**");
```

改之前建议先跑一遍接口回归。这次新增的 `WebMvcConfig` 已经用了正确的 `/**` 写法。

---

## 10. 四个阶段总览

| 阶段 | 内容 | 工具数 | 测试 |
| --- | --- | --- | --- |
| P0 | 对话闭环 + 3 个只读工具 | 3 | — |
| P1 | 附件 + 写工具 + 四道闸 + 确认 + 幂等 + 审计 | 8 | 8 |
| P2 | 多智能体路由 + handoff | 10 | 5 |
| P3 | 异步任务 + 长耗时工具 | 12 | 3 |
| P4 | 封禁体系 + 账号助手 | 14 | 5 |
| **合计** | | **14** | **22** |

---

## 11. 后续可以做的

- **补密钥和 COS**，跑通完整成功路径（上传、批量抓取、AI 扩图）
- **修 Sa-Token 拦截器路径**（见 §9），让注解式鉴权真正生效
- **前端对话 UI**：流式渲染 + 工具调用可视化 + 确认弹窗
- **AI 扩图工具**：需要先修 `AliYunAiApi` 的 token 扣减 bug（用 `spaceUserService.getById(userId)` 查错了表）
- **语义检索**：把图片描述向量化，支持"找那张在海边的照片"
- **评测集**：50 条真实用例，回归工具选择准确率
