# 云图库 Cloud-Gallery

智能协同云图库平台 —— 基于 Spring Boot 的图片管理与协作系统，支持图片上传与检索、私有空间、多人在线协同编辑、AI 扩图与以图搜图。

## 功能特性

- **图片管理**：上传、编辑、删除、审核（待审核 / 通过 / 拒绝）、批量抓取与批量编辑
- **图片检索**：按标签、分类、颜色（相似度）、关键词搜索；支持以图搜图
- **私有空间**：空间分级（普通版 / 专业版 / 旗舰版）、空间配额与用量统计
- **空间分析**：按分类、标签、大小、用户、使用情况多维分析
- **团队协作**：空间成员与角色权限管理（基于 Sa-Token 的细粒度鉴权）
- **协同编辑**：WebSocket + Disruptor 无锁队列实现多人在线实时编辑
- **AI 能力**：阿里云百炼 AI 扩图（OutPainting），并记录 token 消耗
- **图片举报**：用户举报与后台处理流程

## 技术栈

| 分类 | 技术 |
| --- | --- |
| 框架 | Spring Boot 2.7.6、Java 11 |
| 数据层 | MyBatis-Plus 3.5.9、MySQL、ShardingSphere 5.2.0（分库分表） |
| 缓存 / 会话 | Redis、Spring Session、Caffeine、Redisson（分布式锁） |
| 权限 | Sa-Token 1.39.0 |
| 实时通信 | WebSocket、Disruptor 3.4.2 |
| 对象存储 | 腾讯云 COS（cos_api 5.6.227） |
| AI | 阿里云 DashScope（dashscope-sdk-java 2.22.4） |
| 接口文档 | Knife4j 4.4.0（OpenAPI 2） |

## 快速开始

### 1. 环境准备

- JDK 11+
- Maven 3.8+
- MySQL 8.x
- Redis 6.x

### 2. 初始化数据库

```bash
mysql -u root -p < sql/create_table.sql
```

### 3. 配置敏感信息

密钥**不会**提交到仓库。请复制模板并填入自己的密钥：

```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

然后编辑 `application-local.yml`：

```yaml
cos:
  client:
    secretId: <你的腾讯云 SecretId>
    secretKey: <你的腾讯云 SecretKey>

aliyunai:
  apikey: <你的阿里云百炼 API Key>
```

也可以通过环境变量提供，无需创建本地配置文件：

| 环境变量 | 说明 |
| --- | --- |
| `MYSQL_USERNAME` / `MYSQL_PASSWORD` | 数据库账号密码 |
| `COS_HOST` / `COS_SECRET_ID` / `COS_SECRET_KEY` / `COS_REGION` / `COS_BUCKET` | 腾讯云 COS |
| `ALIYUN_AI_API_KEY` | 阿里云百炼 API Key |

### 4. 启动

```bash
mvn spring-boot:run
```

服务默认运行在 <http://localhost:8082/api>，接口文档：<http://localhost:8082/api/doc.html>

## 项目结构

```
src/main/java/com/yupi/
├── api/            # 外部服务调用（阿里云 AI 扩图、以图搜图）
├── aop/            # 权限拦截器
├── annotation/     # 自定义注解（@AutoCheck）
├── configuration/  # 各类配置（CORS、COS、MyBatis-Plus、请求包装）
├── constant/       # 常量
├── controller/     # 接口层
├── exception/      # 全局异常处理
├── manager/        # 业务通用逻辑
│   ├── auth/       # Sa-Token 权限模型与校验
│   ├── sharding/   # 分库分表策略
│   ├── upload/     # 上传模板（文件 / URL）
│   └── websocket/  # 协同编辑（Disruptor 队列）
├── mapper/         # 数据访问层
├── model/          # dto / entity / enums / vo
├── service/        # 业务层
└── utils/          # 工具类（颜色相似度等）
```

## 说明

- `application-local.yml` 已被 `.gitignore` 忽略，用于存放本地密钥，请勿提交。
- 数据库脚本位于 `sql/create_table.sql`。
