# 智能图库系统 SmartGallery

基于 Spring Boot 的智能图库系统，支持图片上传与检索、私有空间、多人在线协同编辑、AI 扩图与以图搜图，
并提供一套**可直接用自然语言操作项目的 AI 智能体（Agent）**。

---

## 功能特性

- **图片管理**：上传、编辑、删除、审核（待审核 / 通过 / 拒绝）、批量抓取与批量编辑
- **图片检索**：按标签、分类、颜色（相似度）、关键词搜索；支持以图搜图
- **私有空间**：空间分级（普通版 / 专业版 / 旗舰版）、空间配额与用量统计
- **空间分析**：按分类、标签、大小、用户、使用情况多维分析
- **团队协作**：空间成员与角色权限管理（基于 Sa-Token 的细粒度鉴权）
- **协同编辑**：WebSocket + Disruptor 无锁队列实现多人在线实时编辑
- **AI 能力**：阿里云百炼 AI 扩图（OutPainting），并记录 token 消耗
- **AI 智能体**：自然语言驱动项目操作，多智能体协作 + 流式对话 + 异步任务（详见下文）
- **图片举报**：用户举报与后台处理流程

---

## AI 智能体

用户可以对着 AI 说人话，由它调用项目已有的 Service 完成真实操作，而不是只能聊天。

### 能做什么

| 用户说 | AI 实际执行 |
| --- | --- |
| "把这张图传到我的图库" + 附件 | 上传到 COS → 写入图片表 → 返回图片 ID |
| "帮我找一下猫的图片" | `picture.search` 按关键词/标签检索 |
| "把它改名叫小猫，加上「萌宠」标签" | `picture.edit` 修改名称与标签 |
| "把这张图删掉" | 触发**二次确认**，确认后 `picture.delete` |
| "我有哪些空间？额度还剩多少？" | `space.listMine` 查询空间与 token |
| "我账号为什么被封了？" | `account.myBanRecords` 读取真实处罚记录并解释 |
| "我要申诉" | `account.submitAppeal` 提交申诉 |
| "帮我批量抓 20 张猫咪图片" | 立即返回任务号，后台执行，可查进度 |

### 多智能体架构

采用「**路由 + 专家 + 转交**」而不是自由群聊，兼顾准确率与成本：

```
用户输入
   │
   ├─ 1. 规则命中（0 次 LLM 调用，覆盖绝大多数常见说法）
   ├─ 2. LLM 分类（qwen-turbo，32 token，规则没命中时才走）
   └─ 3. 兜底 → 通用助手
        │
        ├── 图片管家  上传 / 检索 / 编辑 / 删除 / 批量抓取
        ├── 空间管家  容量 / 用量 / 额度 / 统计
        ├── 账号助手  账号状态 / 封禁 / 申诉 / 余额
        └── 通用助手  闲聊与兜底
```

每个智能体只拿到**自己的工具白名单**，既省 token，也从根上降低误操作概率
（空间管家根本看不到 `picture.delete`）。

支持 `agent.handoff` 转交：模型发现不归自己管时主动切换，单轮最多 2 跳防乒乓。

### 14 个工具

| 域 | 工具 |
| --- | --- |
| 图片 | `picture.search`、`picture.getDetail`、`picture.uploadByUrl`、`picture.uploadFromAttachment`、`picture.edit`、`picture.delete`、`picture.batchUpload` |
| 空间 | `space.listMine` |
| 账号 | `account.myStatus`、`account.myBanRecords`、`account.submitAppeal` |
| 任务 | `task.getStatus` |
| 通用 | `help.listCapabilities`、`agent.handoff` |

### 安全与治理

- **四道闸门**（`ToolGuard`）：封禁校验 → 权限校验 → 必填校验 → 高危确认
- **高危二次确认**：删除等不可恢复操作先中断，把推理现场存入 Redis；
  用户确认后**从断点续跑**，而不是从头重来
- **工具幂等**：以 `toolCallId` 去重，SSE 重连/前端重试不会重复执行
- **全量审计**：每次工具调用落 `agent_tool_audit`（入参、出参、耗时、成功与否）
- **身份服务端注入**：`userId` 永远来自会话快照，不接受模型传入，杜绝越权
- **封禁用户降级**：被封用户仍可登录并使用智能体（只读 + 申诉），业务接口拦截

### 上下文压缩

长对话不会撑爆上下文，也不会丢关键信息：

```
System Prompt → 结构化记忆（关键 ID）→ 滚动摘要 → 最近 N 轮原文 → 当前消息
```

- **按 token 预算触发**（字符启发式估算），不按轮数硬砍
- **增量压缩**：水位 `compressedUntilId` 记录进度，只压还没进摘要的部分
- **结构化记忆**：图片/空间/任务 ID 单独存 JSON 原样回灌（雪花 ID 19 位，自由文本摘要极易写错）
- **降级**：摘要调用失败则退回纯截断，不阻塞对话

### 异步任务

长耗时操作（批量抓图）以「立即返回任务号 + 后台执行 + 进度可查」的方式接入，
避免用户干等；Redis 做用户级并发锁，进程崩溃靠 TTL 自愈。

### 前端对话界面

零构建单页应用，由 Spring Boot 直接托管：

```
http://localhost:8082/api/agent-ui/
```

包含登录、会话管理、SSE 流式渲染、工具调用可视化、确认弹窗、附件上传、任务看板。

---

## 技术栈

| 分类 | 技术 |
| --- | --- |
| 框架 | Spring Boot 2.7.6、Java 11 |
| 数据层 | MyBatis-Plus 3.5.9、MySQL、ShardingSphere 5.2.0（分库分表） |
| 缓存 / 会话 | Redis、Spring Session、Caffeine、Redisson（分布式锁） |
| 权限 | Sa-Token 1.39.0 |
| 实时通信 | WebSocket、Disruptor 3.4.2 |
| 对象存储 | 腾讯云 COS（cos_api 5.6.227） |
| AI 模型 | 阿里云百炼千问（OpenAI 兼容协议，`qwen-plus` / `qwen-turbo`） |
| 接口文档 | Knife4j 4.4.0（OpenAPI 2） |

> 智能体默认使用 `qwen-plus` 处理对话与工具调用，`qwen-turbo` 做意图分类与摘要压缩。
> 走的是 OpenAI 兼容协议，换任何兼容厂商只需改 `agent.llm.base-url`。

---

## 快速开始

### 1. 环境准备

- JDK 11+（**注意**：项目的 Lombok 1.18.24 不支持 JDK 21，请用 JDK 11 或 17）
- Maven 3.8+
- MySQL 8.x
- Redis 6.x

### 2. 初始化数据库

```bash
mysql -u root -p < sql/create_table.sql
mysql -u root -p < sql/agent.sql      # 智能体相关表（会话/消息/附件/任务/审计/封禁）
```

### 3. 配置敏感信息

密钥**不会**提交到仓库。复制模板并填入自己的配置：

```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

编辑 `application-local.yml`：

```yaml
spring:
  datasource:
    username: root
    password: <你的数据库密码>

cos:
  client:
    host: <你的 COS 域名>
    secretId: <你的腾讯云 SecretId>
    secretKey: <你的腾讯云 SecretKey>
    region: ap-shanghai
    bucket: <你的桶名>

# 阿里云百炼（千问）密钥，智能体依赖它
aliYunOssAi:
  apiKey: <你的阿里云百炼 API Key>
```

也可以通过环境变量提供，无需创建本地配置文件：

| 环境变量 | 说明 |
| --- | --- |
| `MYSQL_USERNAME` / `MYSQL_PASSWORD` | 数据库账号密码 |
| `COS_HOST` / `COS_SECRET_ID` / `COS_SECRET_KEY` / `COS_REGION` / `COS_BUCKET` | 腾讯云 COS |
| `ALIYUN_AI_API_KEY` 或 `DASHSCOPE_API_KEY` | 阿里云百炼 API Key |

> 项目默认激活 `local` profile（`spring.profiles.active: ${SPRING_PROFILES_ACTIVE:local}`），
> 会读取 `application-local.yml`。该文件已被 `.gitignore` 忽略。

### 4. 启动

```bash
mvn spring-boot:run
```

| 入口 | 地址 |
| --- | --- |
| 服务根路径 | <http://localhost:8082/api> |
| **智能体对话界面** | <http://localhost:8082/api/agent-ui/> |
| **接口文档（Knife4j）** | <http://localhost:8082/api/doc.html> |

> 接口文档使用 Knife4j 4.4.0 + OpenAPI 3（底层 springdoc）。
> 注意不要换回 `knife4j-openapi2-spring-boot-starter`——它底层的 springfox 2.10.5
> 与 Spring Boot 2.6+ 的 `PathPatternParser` 不兼容，启动会抛
> `WebMvcPatternsRequestConditionWrapper.getPatterns` 空指针，导致应用起不来。

---

## 智能体接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/agent/chat` | 非流式对话（调试 / 降级） |
| POST | `/agent/chat/stream` | **SSE 流式对话**（主接口） |
| POST | `/agent/chat/async` | 提交异步对话，返回任务号 |
| POST | `/agent/confirm` `·` `/agent/confirm/stream` | 高危操作确认 / 拒绝 |
| POST | `/agent/attachment/upload` | 上传对话附件 |
| GET | `/agent/agents` | 智能体与工具清单 |
| GET `·` DELETE | `/agent/session/list` `·` `/agent/session/{id}` | 会话列表 / 删除 |
| GET | `/agent/session/{id}/messages` | 历史消息 |
| GET `·` POST | `/agent/task/{taskId}` `·` `/agent/task/{taskId}/cancel` | 任务查询 / 取消 |

### SSE 事件

`agent_switch`（智能体切换或转交）、`session`、`tool_call`、`tool_result`、
`confirm_required`（需确认）、`delta`（文本增量）、`done`、`error`。

### 一个最小示例

```bash
# 登录
curl -c cookie.txt -X POST http://localhost:8082/api/user/login \
  -H 'Content-Type: application/json' \
  -d '{"userAccount":"your_account","userPassword":"your_password"}'

# 流式对话
curl -N -b cookie.txt -X POST http://localhost:8082/api/agent/chat/stream \
  -H 'Content-Type: application/json' \
  -d '{"message":"我有哪些空间？"}'
```

---

## 项目结构

```
src/main/java/com/yupi/
├── agent/              # ★ AI 智能体
│   ├── agent/          #   子智能体（图片/空间/账号/通用）
│   ├── attachment/     #   对话附件上传与消费
│   ├── audit/          #   工具调用审计
│   ├── config/         #   智能体配置与线程池
│   ├── controller/     #   /agent/** 接口
│   ├── core/           #   编排器、路由、身份快照、确认现场
│   ├── llm/            #   LLM 客户端抽象与千问实现（含 SSE 解析）
│   ├── memory/         #   上下文压缩（token 估算 / 摘要 / 结构化记忆）
│   ├── model/          #   dto / vo
│   ├── session/        #   会话与消息存储
│   ├── task/           #   异步任务
│   └── tool/           #   工具契约、注册表、四道闸、14 个工具实现
├── api/                # 外部服务调用（阿里云 AI 扩图、以图搜图）
├── aop/                # 权限拦截、封禁拦截
├── annotation/         # 自定义注解（@AutoCheck）
├── configuration/      # 各类配置（CORS、COS、MyBatis-Plus、线程池）
├── constant/           # 常量
├── controller/         # 接口层
├── exception/          # 全局异常处理
├── manager/            # 业务通用逻辑
│   ├── auth/           # Sa-Token 权限模型与校验
│   ├── sharding/       # 分库分表策略
│   ├── upload/         # 上传模板（文件 / URL）
│   └── websocket/      # 协同编辑（Disruptor 队列）
├── mapper/             # 数据访问层
├── model/              # dto / entity / enums / vo
├── service/            # 业务层
└── utils/              # 工具类（颜色相似度等）

src/main/resources/
├── static/agent-ui/    # ★ 智能体对话前端（零构建 HTML/CSS/JS）
├── mapper/             # MyBatis XML
└── application*.yml
```

---

## 文档

智能体的设计与实现细节在 [`docs/agent/`](docs/agent/)：

| 文档 | 内容 |
| --- | --- |
| [AGENT_DESIGN.md](docs/agent/AGENT_DESIGN.md) | 整体方案：架构、工具清单、数据模型、接口契约、路线图 |
| [P0_IMPLEMENTATION.md](docs/agent/P0_IMPLEMENTATION.md) | 对话闭环骨架 |
| [P1_IMPLEMENTATION.md](docs/agent/P1_IMPLEMENTATION.md) | 写能力、附件、四道闸、确认、幂等、审计 |
| [P2_IMPLEMENTATION.md](docs/agent/P2_IMPLEMENTATION.md) | 多智能体路由与转交 |
| [P3_IMPLEMENTATION.md](docs/agent/P3_IMPLEMENTATION.md) | 异步任务与长耗时工具 |
| [P4_IMPLEMENTATION.md](docs/agent/P4_IMPLEMENTATION.md) | 封禁体系与账号助手 |
| [P5_FRONTEND.md](docs/agent/P5_FRONTEND.md) | 前端对话界面 |
| [P6_CONTEXT_COMPRESSION.md](docs/agent/P6_CONTEXT_COMPRESSION.md) | 上下文压缩 |
| [SECURITY_FIX.md](docs/agent/SECURITY_FIX.md) | 鉴权漏洞修复记录 |

---

## 测试

```bash
mvn test
```

覆盖工具注册与权限、多智能体路由、异步任务生命周期、封禁流程、上下文压缩等，共 32 个用例。

> 涉及真实 LLM 与 COS 的链路需要配置有效密钥后才能跑通。

---

## 说明

- `application-local.yml` 已被 `.gitignore` 忽略，用于存放本地密钥，请勿提交。
- 数据库脚本位于 `sql/create_table.sql`（基础表）与 `sql/agent.sql`（智能体相关表）。
- **分库分表已停用**：启动类 `exclude` 了 `ShardingSphereAutoConfiguration`，
  数据源改由 `spring.datasource` 提供。若要重新启用，请注释掉该数据源配置。
