# 智能体 P0 实现说明

> 对应设计文档：[AGENT_DESIGN.md](./AGENT_DESIGN.md)　版本：v1.0 已实现
> 代码基线：Spring Boot 2.7.6 / Java 11

---

## 1. 一句话

打通了「用户发一句话 → LLM 决策 → 调用只读工具 → 流式返回真实数据」的最小闭环。
**不含**写操作、附件上传、异步任务、多智能体、封禁体系（这些是 P1~P4）。

---

## 2. 实际落地的文件

### 2.1 新增代码（`com.yupi.agent`）

```
com/yupi/agent/
├── config/
│   ├── AgentProperties.java           # @ConfigurationProperties("agent")
│   └── AgentExecutorConfig.java       # agentExecutor / agentSseExecutor 两个线程池
├── llm/
│   ├── LlmClient.java                 # chat / streamChat
│   ├── DashScopeLlmClient.java        # OpenAI 兼容协议实现（Hutool HTTP，手写 SSE 解析）
│   ├── LlmException.java              # 带 retryable 标记
│   └── model/{ChatMessage,ChatRequest,ChatResponse,ToolCall,ToolSpec}.java
├── core/
│   ├── AgentContext.java              # 不可变身份快照
│   ├── AgentContextFactory.java       # 请求线程内构建快照
│   ├── AgentRouter.java               # P0 恒返回 general，P2 填规则+LLM 分类
│   ├── AgentOrchestrator.java         # ReAct 主循环
│   └── AgentException.java
├── agent/
│   ├── SubAgent.java
│   ├── GeneralAgent.java              # P0 唯一智能体
│   └── SubAgentRegistry.java
├── tool/
│   ├── AgentTool.java / ToolRiskLevel.java / ToolResult.java / ToolRegistry.java
│   └── impl/
│       ├── HelpListCapabilitiesTool.java   # help.listCapabilities
│       ├── PictureSearchTool.java          # picture.search
│       └── SpaceListMineTool.java          # space.listMine
├── session/AgentSessionStore.java     # MySQL 为准 + Redis 热缓存
├── model/dto/ChatRequestDTO.java
├── model/vo/{ChatEventVO,ChatResponseVO,ToolCallRecordVO,SessionVO,MessageVO}.java
└── controller/
    ├── AgentChatController.java       # /agent/chat、/agent/chat/stream
    └── AgentSessionController.java    # /agent/session/**
```

### 2.2 新增实体与 Mapper

- `model/entity/AgentSession.java`、`model/entity/AgentMessage.java`
- `mapper/AgentSessionMapper.java`、`mapper/AgentMessageMapper.java`（**放在 `com.yupi.mapper`**，因为启动类是 `@MapperScan("com.yupi.mapper")`）

### 2.3 建表脚本

`sql/agent.sql` —— `agent_session`、`agent_message` 两张表。

> ⚠️ 列名使用**驼峰**（`sessionId`、`toolCallId`），因为项目配置了
> `mybatis-plus.configuration.map-underscore-to-camel-case: false`。

### 2.4 修改的既有文件

| 文件 | 改动 | 原因 |
| --- | --- | --- |
| `application.yml` | 新增 `agent:` 配置块 | 功能配置 |
| | 新增 `spring.mvc.async.request-timeout: 180000` | SSE 超时 |
| | 新增 `spring.main.allow-circular-references: true` | **既有问题**，见 §4 |
| | 新增 `spring.mvc.pathmatch.matching-strategy: ant_path_matcher` | **既有问题**，见 §4 |
| | `knife4j.enable: true → false` | **既有问题**，见 §4 |
| `AliYunAiApi.java` | `@Value("${aliYunOssAi.tokenCost")` → `@Value("${aliYunOssAi.tokenCost:10}")` | **既有致命 bug**，占位符少 `}` 导致启动失败 |
| `PictureServiceImpl.java` | 删除内部 `@Bean("taskExecutor")` 方法 | **既有问题**：`@Bean` 写在 `@Service` 里 + 自身 `@Resource(name="taskExecutor")` 形成自引用 |
| `configuration/TaskExecutorConfig.java` | **新增**，承接上面的线程池定义 | 同上 |
| `SpaceAnalyzeServiceImpl.java` | `spaceService`、`pictureService` 加 `@Lazy` | **既有循环依赖** |
| `PictureServiceImpl.java:320` | `sortOrder` 判空 | **既有 NPE**，`picture.search` 必崩 |
| `UserServiceImpl.java:229` | `sortOrder` 判空 | **既有 NPE** |

---

## 3. 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/agent/chat` | 非流式对话（调试 / 降级） |
| POST | `/api/agent/chat/stream` | SSE 流式对话 |
| GET | `/api/agent/session/list` | 会话列表 |
| GET | `/api/agent/session/{id}/messages` | 历史消息 |

请求体：

```json
{ "sessionId": 123, "message": "我有哪些空间？", "agentType": null }
```

SSE 事件类型：`agent_switch` / `session` / `tool_call` / `tool_result` / `delta` / `done` / `error`。

---

## 4. ⚠️ 已验证的既有启动阻塞（与本功能无关）

我用 `git stash` 在**干净基线**上跑过一次，确认项目当前提交状态下**无法启动**。
以下是逐个排查出来的阻塞项，P0 已一并修复：

| # | 问题 | 表现 | 处理 |
| --- | --- | --- | --- |
| 1 | `AliYunAiApi.java:41` `@Value` 占位符少 `}` | `NumberFormatException: For input string: "${aliYunOssAi.tokenCost"` | 补 `}` 并给默认值 |
| 2 | `SpaceServiceImpl` ↔ `SpaceAnalyzeServiceImpl` 互相注入 | `BeanCurrentlyInCreationException` | 加 `@Lazy` |
| 3 | `PictureService → SpaceService → SpaceAnalyzeService → PictureService` | 同上 | 加 `@Lazy` |
| 4 | `PictureServiceImpl` 内部 `@Bean("taskExecutor")` + 自身注入它，且该类被 `@Async` 代理 | `injected in its raw version ... but has eventually been wrapped` | 线程池移到 `TaskExecutorConfig` |
| 5 | springfox 2.10.5 + Spring Boot 2.7 | `WebMvcPatternsRequestConditionWrapper.getPatterns` NPE | **临时关闭 knife4j**（见下） |

关于 #5：`spring.mvc.pathmatch.matching-strategy: ant_path_matcher` 对 springfox **2.10.5 无效**
（该配置主要解决 springfox 3.x 的问题）。我做过对照实验：把两个 agent Controller 移出源码后
NPE 依然复现，确认与本功能无关。

**因此 `knife4j.enable` 被置为 `false`，接口文档 `/api/doc.html` 暂时不可用。**
彻底修复的两条路：
1. 迁移到 **springdoc-openapi**（推荐，springfox 已停止维护）；
2. 自定义 `WebMvcRequestHandlerProvider` 兼容 `PathPatternParser`。

修好后把 `knife4j.enable` 改回 `true` 即可。

---

## 5. 验证结果

### 5.1 编译

```
mvn -o -DskipTests compile   → BUILD SUCCESS
```

> 注意：本机默认 JDK 是 21，项目 Lombok 1.18.24 不支持 JDK 21，会报
> `NoSuchFieldError: JCTree$JCImport ... qualid`。
> 编译请用 JDK 11（`D:\java-install\java-11`）或 JDK 17。

### 5.2 启动

```
Tomcat started on port(s): 8082 (http) with context path '/api'
SubAgentRegistry : 智能体注册完成，共 1 个：[general]
ToolRegistry     : 智能体工具注册完成，共 3 个：[help.listCapabilities, picture.search, space.listMine]
Started Project2025122CloudGalleryApplication in 7.733 seconds
```

### 5.3 接口

| 用例 | 结果 |
| --- | --- |
| `POST /api/agent/chat` 未登录 | `{"code":40100,"message":"未登录"}` HTTP 200 |
| `POST /api/agent/chat/stream` 未登录 | `{"code":40100,"message":"未登录"}` HTTP 200（**不是 406/500**） |
| `POST /api/agent/chat` 空消息 | `{"code":40000,"message":"消息内容不能为空"}` |
| `GET /api/agent/session/list` 未登录 | `{"code":40100,"message":"未登录"}` |
| 回归 `GET /api/picture/tag_category` | 正常返回标签/分类 |

### 5.4 尚未验证（需要你提供环境）

- **真实对话闭环**：需要先执行 `sql/agent.sql` 建表，并用有效账号登录拿 Cookie。
- **LLM 调用**：需要有效的 `DASHSCOPE_API_KEY`。

我没有 MySQL 的 root 密码，所以这两步没能跑通。

---

## 6. 快速自测

```bash
# 1. 建表
mysql -uroot -p < sql/agent.sql

# 2. 配置密钥（二选一）
export DASHSCOPE_API_KEY=sk-xxxx
# 或复用已有配置 aliYunOssAi.apiKey

# 3. 启动（用 JDK 11）
mvn spring-boot:run

# 4. 登录拿 Cookie
curl -c cookie.txt -X POST http://localhost:8082/api/user/login \
     -H 'Content-Type: application/json' \
     -d '{"userAccount":"your_account","userPassword":"your_password"}'

# 5. 非流式
curl -b cookie.txt -X POST http://localhost:8082/api/agent/chat \
     -H 'Content-Type: application/json' -d '{"message":"我有哪些空间？"}'

# 6. 流式
curl -N -b cookie.txt -X POST http://localhost:8082/api/agent/chat/stream \
     -H 'Content-Type: application/json' -d '{"message":"帮我找一下猫的图片"}'
```

预期：第 5 步返回 `data.content` 里是自然语言回答，`data.toolCalls` 里能看到 `space.listMine` 的执行记录。

---

## 7. 与设计文档的偏差

| 设计文档 | 实际实现 | 原因 |
| --- | --- | --- |
| 独立的 `ToolContext` 类 | 工具直接接收 `AgentContext` | P0 没有附件/确认态，少一层包装；P1 引入附件时再抽出 `ToolContext` |
| 每个域一个 `PictureTools` 聚合类 | 一个工具一个 `@Component` 类 | Spring 直接收集 `List<AgentTool>`，更简单 |
| `dashscope-sdk-java` | 手写 OpenAI 兼容协议 HTTP | 报文可控、流式与 function calling 行为稳定、换厂商成本低 |
| 工具结果 500 字符截断 | 已实现（`agent.chat.tool-result-max-chars`） | — |
| 审计表 `agent_tool_audit` | P0 未建，工具调用已落 `agent_message`（role=tool） | P1 建独立审计表 |
| Redis 热缓存 | 已实现（读回填 / 写失效） | — |

---

## 8. 下一步（P1）

1. `agent_attachment` 表 + 附件上传接口（复用 COS）
2. `picture.uploadByUrl` / `uploadFromAttachment` / `edit` 工具
3. `ToolGuard` 四道闸 + `agent_tool_audit` 审计表
4. 高危操作二次确认（`picture.delete` + `ConfirmTokenStore`）
5. 幂等（`SETNX toolCallId`）
6. 顺手修 `PictureServiceImpl` 公共图库上传/删除失败（设计文档 §12 #3 #4）
