# 智能体前端对话界面

> 配套后端：[P0](./P0_IMPLEMENTATION.md) ~ [P4](./P4_IMPLEMENTATION.md)　设计：[AGENT_DESIGN.md](./AGENT_DESIGN.md)

---

## 1. 一句话

给智能体配了一个**零构建、开箱即用**的对话界面：
登录、多轮对话、SSE 流式输出、工具调用可视化、高危操作确认弹窗、附件上传、异步任务看板。

---

## 2. 怎么打开

启动应用后访问：

```
http://localhost:8082/api/agent-ui/
```

（会自动跳转到 `/api/agent-ui/index.html`）

用项目里已有的账号登录即可。没有账号的话，注册接口需要管理员权限，可以直接在数据库插一条，
或用一个已有账号。

---

## 3. 技术选型：为什么不用 React / Vue

| 方案 | 取舍 |
| --- | --- |
| **原生 HTML + CSS + JS**（本次采用） | 零构建、零依赖，改完刷新即可；缺点是组件化能力弱 |
| Vite + React/Vue | 组件化好，但要引入 node_modules、构建步骤、跨域配置，对一个「验证智能体能力」的页面来说过重 |

本项目是纯后端工程（`src/main/resources/static/` 只有一个 hello world 测试页），
放一个单页应用进去、由 Spring Boot 直接托管，是最省事的路径。

三个文件，加起来约 46 KB：

```
src/main/resources/static/agent-ui/
├── index.html   4 KB    结构
├── style.css   15 KB    样式
└── app.js      27 KB    逻辑
```

---

## 4. 功能清单

### 4.1 登录

- 进入页面先调 `GET /user/get/login` 探测登录态
- 未登录显示登录卡片，登录成功后直接进入主界面
- 侧边栏显示当前用户（管理员会标注）

### 4.2 对话

- 左侧会话列表，点击切换、可删除
- 「新建对话」清空当前上下文
- 首条消息自动成为会话标题（后端生成）
- 空状态给出 4 个示例问题，点击填入输入框

### 4.3 SSE 流式输出

后端用的是 `POST + text/event-stream`，而浏览器原生 `EventSource` **只支持 GET**，
所以这里用 `fetch` + `ReadableStream` 手写了解析器：

```js
const reader = resp.body.getReader();
const decoder = new TextDecoder('utf-8');
let buffer = '';
while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    buffer = buffer.replace(/\r\n/g, '\n');
    let idx;
    while ((idx = buffer.indexOf('\n\n')) >= 0) {
        const block = buffer.slice(0, idx);
        buffer = buffer.slice(idx + 2);
        const evt = parseEvent(block);   // 取 data: 行并 JSON.parse
        if (evt) onEvent(evt);
    }
}
```

> 注意：出错时后端返回的是普通 JSON（不是 SSE），所以先检查 `Content-Type`，
> 不匹配就直接解析 JSON 并抛错——否则会卡在流里。

### 4.4 事件处理

| 事件 | 前端行为 |
| --- | --- |
| `agent_switch` | 更新右上角智能体徽章；若是转交，插一条分割线「转交给「空间管家」：原因」 |
| `session` | 记录 sessionId（首轮新建时拿到） |
| `tool_call` | 插入一张工具卡片（工具名 + 参数），状态「执行中」 |
| `tool_result` | 卡片状态改为「完成/失败」，展示结果摘要，默认折叠、可点击展开 |
| `delta` | 追加到当前助手气泡，带闪烁光标 |
| `confirm_required` | 弹出确认框，展示操作描述与参数 |
| `done` | 移除光标，刷新会话列表 |
| `error` | 在气泡下方插入红色错误块 |

### 4.5 高危操作确认

`picture.delete` 这类工具会触发 `confirm_required`，前端弹窗展示：

```
⚠️ 需要确认
即将删除图片「小猫」（ID=123），该操作不可恢复，确认继续吗？
{ "pictureId": 123 }
[取消]  [确认执行]
```

点确认后调 `POST /agent/confirm/stream`，后端从 Redis 取出推理现场**接着往下跑**，
前端复用同一套流式渲染展示后续回答。

支持链式确认：确认流程里又触发新的高危操作时，弹窗会再次出现。

### 4.6 附件上传

- 点 📎 选择图片（jpeg/png/webp，≤10MB）
- 调 `POST /agent/attachment/upload` 拿到 `attachmentId` 和预览 URL
- 输入框上方显示缩略图 chip，可移除
- 发送时把 `attachmentIds` 一起提交

后端收到后会把附件信息拼进用户消息：

```
【用户附带了 1 个图片附件】
- attachmentId=123
如需保存到图库，请调用 picture.uploadFromAttachment 并传入对应的 attachmentId。
```

这样模型就知道该调哪个工具、传什么参数。

### 4.7 异步任务看板

侧边栏下半部分，每 5 秒轮询 `GET /agent/task/list`，展示：

```
a1b2c3d4        [执行中]
已上传 12/30        40%
▬▬▬▬▬▬▬░░░░░░░░
```

状态色：排队中/执行中（黄）、完成（绿）、失败/取消（红）。

---

## 5. 后端配套改动

| 文件 | 改动 | 原因 |
| --- | --- | --- |
| `ChatRequestDTO` | 新增 `attachmentIds` | 前端需要把附件 id 传给后端 |
| `AgentOrchestrator` | 新增 `buildUserContent()`，把附件提示拼进用户消息 | 让模型知道有图可操作 |
| `ChatEventVO` | 新增 `confirmToken` 字段 | 之前确认令牌塞在 `message` 字段里，语义混乱 |
| `AgentUiController` | 新增 `/agent-ui` → `/agent-ui/index.html` 的跳转 | Spring Boot 欢迎页只对根路径生效 |
| `WebMvcConfig` | 封禁拦截器放行 `/agent-ui/**` | 被封用户也要能打开对话页 |

---

## 6. 页面结构

```
┌──────────────┬────────────────────────────────────────────┐
│  云图库 AI    │  新对话              ● 图片管家              │
│              ├────────────────────────────────────────────┤
│  ＋ 新建对话  │                                            │
│              │   🙋 你                                     │
│  最近对话     │   把这张图传到图库                          │
│  · 我有哪些空间│                                            │
│  · 帮找猫的图 │   🤖 图片管家                                │
│              │   🔧 picture.uploadFromAttachment  [完成]   │
│  异步任务     │   🤖 已经帮你传好啦，图片 ID 是 123…        │
│  · a1b2 [完成]│                                            │
│              │  ┌──────────────────────────────────────┐  │
│              │  │ 📎  说点什么…                     ↑ │  │
│  👤 用户名    │  └──────────────────────────────────────┘  │
└──────────────┴────────────────────────────────────────────┘
```

---

## 7. 已知限制

| 限制 | 说明 |
| --- | --- |
| 无 Markdown 渲染 | 助手回复按纯文本展示（`white-space: pre-wrap`）。要渲染 Markdown 需引入 marked.js |
| 历史消息不含工具卡片 | 会话回看时只展示 user/assistant 的文本消息，工具调用记录在数据库里但前端没回放 |
| 无重连 | SSE 断开后不自动重连，需重新发送 |
| 无多实例会话 | 附件上传后存在单库，多实例部署时任务锁靠 Redis 已可跨实例，但附件消费依赖 COS |
| 移动端简化 | < 820px 时隐藏侧边栏，没有做抽屉 |

---

## 8. 自测路径

```bash
# 1. 启动
mvn spring-boot:run

# 2. 浏览器打开
#    http://localhost:8082/api/agent-ui/

# 3. 登录（用已有账号）

# 4. 依次试这几句：
#    - 我有哪些空间？          → 路由到 space，调 space.listMine
#    - 我账号为什么被封了？     → 路由到 account，调 account.myBanRecords
#    - 帮我找一下最近的图片     → 路由到 picture，调 picture.search
#    - 把「xxx」删掉           → 触发 confirm_required，弹出确认框
```

> 前提：`application-local.yml` 里配好有效的 `aliYunOssAi.apiKey`，
> 否则会看到「AI 密钥无效或已过期」的错误气泡（前端会正确展示，不会白屏）。

---

## 9. 后续可以加的

- Markdown + 代码高亮渲染
- 历史消息回放工具卡片（后端 `agent_message` 里 `role=tool` 的记录已有，需要接口返回）
- 语音输入
- 对话导出
- 深色模式
