-- ============================================================
-- 智能体（Agent）功能 P0 建表脚本
-- 说明：
--   1. 本项目 mybatis-plus 配置了 map-underscore-to-camel-case=false，
--      因此列名统一使用驼峰命名，与现有表保持一致。
--   2. 逻辑删除字段统一为 isDelete。
-- ============================================================

use yu_picture;

-- 1. 智能体会话
create table if not exists agent_session
(
    id              bigint auto_increment comment 'id' primary key,
    userId          bigint                             not null comment '用户 id',
    title           varchar(128)                       null comment '会话标题',
    agentType       varchar(32)  default 'general'     not null comment '当前智能体',
    summary         text                               null comment '历史摘要（长对话压缩用）',
    messageCount    int          default 0             not null comment '消息条数',
    lastMessageTime datetime                           null comment '最后消息时间',
    createTime      datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime      datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete        tinyint      default 0             not null comment '是否删除',
    index idx_userId_updateTime (userId, updateTime)
) comment '智能体会话' collate = utf8mb4_unicode_ci;

-- 2. 智能体消息
create table if not exists agent_message
(
    id               bigint auto_increment comment 'id' primary key,
    sessionId        bigint       not null comment '会话 id',
    userId           bigint       not null comment '用户 id',
    role             varchar(16)  not null comment 'user/assistant/tool/system',
    agentType        varchar(32)  null comment '产生该消息的智能体',
    content          text         null comment '文本内容',
    toolName         varchar(64)  null comment '工具名（role=tool）',
    toolCallId       varchar(64)  null comment 'LLM 工具调用 id',
    toolArgs         text         null comment '工具入参 JSON',
    toolResult       text         null comment '工具出参 JSON',
    status           varchar(16)  default 'SUCCESS' not null comment 'SUCCESS/FAILED/CANCELED',
    promptTokens     int          default 0 not null comment '输入 token',
    completionTokens int          default 0 not null comment '输出 token',
    latencyMs        int          default 0 not null comment '耗时（毫秒）',
    createTime       datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    isDelete         tinyint      default 0 not null comment '是否删除',
    index idx_sessionId_id (sessionId, id),
    index idx_userId_createTime (userId, createTime)
) comment '智能体消息' collate = utf8mb4_unicode_ci;


-- ============================================================
-- P1：附件、工具审计
-- ============================================================

-- 3. 智能体附件（用户在对话里上传的图片）
create table if not exists agent_attachment
(
    id          bigint auto_increment comment 'id' primary key,
    userId      bigint        not null comment '用户 id',
    sessionId   bigint        null comment '会话 id（可为空，上传时还没有会话）',
    url         varchar(1024) not null comment 'COS 地址',
    cosKey      varchar(512)  null comment 'COS 对象 key（消费后可删除）',
    fileName    varchar(256)  null comment '原始文件名',
    contentType varchar(128)  null comment 'MIME',
    fileSize    bigint        null comment '字节数',
    status      varchar(16)   default 'UPLOADED' not null comment 'UPLOADED/CONSUMED',
    createTime  datetime      default CURRENT_TIMESTAMP not null comment '创建时间',
    isDelete    tinyint       default 0 not null comment '是否删除',
    index idx_userId (userId),
    index idx_status (status)
) comment '智能体附件' collate = utf8mb4_unicode_ci;

-- 4. 工具调用审计
create table if not exists agent_tool_audit
(
    id         bigint auto_increment comment 'id' primary key,
    traceId    varchar(64)   null comment '链路 id',
    sessionId  bigint        null comment '会话 id',
    taskId     varchar(64)   null comment '任务 id（P3 异步任务用）',
    userId     bigint        not null comment '用户 id',
    agentType  varchar(32)   null comment '智能体',
    toolName   varchar(64)   not null comment '工具名',
    riskLevel  varchar(16)   not null comment 'READ_ONLY/WRITE/DANGEROUS',
    argsJson   text          null comment '入参',
    resultJson text          null comment '出参摘要',
    success    tinyint       default 1 not null comment '是否成功',
    errorMsg   varchar(1024) null comment '错误信息',
    costMs     int           default 0 not null comment '耗时（毫秒）',
    createTime datetime      default CURRENT_TIMESTAMP not null comment '创建时间',
    index idx_userId_createTime (userId, createTime),
    index idx_toolName (toolName)
) comment '智能体工具调用审计' collate = utf8mb4_unicode_ci;


-- ============================================================
-- P3：异步任务
-- ============================================================

-- 5. 智能体异步任务
create table if not exists agent_task
(
    id              bigint auto_increment comment 'id' primary key,
    taskId          varchar(64)    not null comment '业务任务号',
    userId          bigint         not null comment '用户 id',
    sessionId       bigint         null comment '会话 id',
    type            varchar(32)    not null comment 'CHAT/BATCH_UPLOAD',
    status          varchar(16)    default 'PENDING' not null comment 'PENDING/RUNNING/SUCCESS/FAILED/CANCELED',
    progress        int            default 0 not null comment '进度 0-100',
    progressMessage varchar(256)   null comment '进度描述',
    inputJson       text           null comment '输入',
    outputJson      text           null comment '输出',
    errorMsg        varchar(1024)  null comment '错误信息',
    createTime      datetime       default CURRENT_TIMESTAMP not null comment '创建时间',
    startTime       datetime       null comment '开始时间',
    endTime         datetime       null comment '结束时间',
    updateTime      datetime       default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    unique key uk_taskId (taskId),
    index idx_userId_status (userId, status),
    index idx_userId_createTime (userId, createTime)
) comment '智能体异步任务' collate = utf8mb4_unicode_ci;


-- ============================================================
-- P4：用户封禁体系（支撑"我被封了，为什么？"）
-- ============================================================

-- 6. 用户封禁记录
create table if not exists user_ban_record
(
    id            bigint auto_increment comment 'id' primary key,
    userId        bigint        not null comment '被处罚用户 id',
    banType       tinyint       default 1 not null comment '1-封禁 2-禁言 3-限制上传',
    reasonCode    varchar(64)   null comment '原因码：VIOLENT_CONTENT/SPAM/COPYRIGHT/OTHER',
    reasonDetail  varchar(1024) null comment '详细原因（面向用户展示）',
    relatedId     bigint        null comment '关联对象 id（如被举报图片 id）',
    operatorId    bigint        null comment '操作人 id',
    startTime     datetime      not null comment '开始时间',
    endTime       datetime      null comment '结束时间（空=永久）',
    status        tinyint       default 0 not null comment '0-生效中 1-已解封 2-已过期',
    unbanReason   varchar(512)  null comment '解封原因',
    appealStatus  tinyint       default 0 not null comment '0-未申诉 1-申诉中 2-通过 3-驳回',
    appealContent varchar(1024) null comment '申诉内容',
    appealTime    datetime      null comment '申诉时间',
    appealReply   varchar(512)  null comment '申诉处理意见',
    createTime    datetime      default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime    datetime      default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete      tinyint       default 0 not null comment '是否删除',
    index idx_userId_status (userId, status),
    index idx_status_createTime (status, createTime)
) comment '用户封禁记录' collate = utf8mb4_unicode_ci;


-- ============================================================
-- P5：上下文压缩（分层上下文 + 增量摘要 + 结构化记忆）
-- ============================================================

-- agent_session.summary 在 P0 建表时已存在，这里补两列
ALTER TABLE agent_session
    ADD COLUMN memory text NULL COMMENT '结构化记忆 JSON（关键实体：图片/空间/任务 id 等）',
    ADD COLUMN compressedUntilId bigint NULL COMMENT '已压缩到的消息 id 水位';
