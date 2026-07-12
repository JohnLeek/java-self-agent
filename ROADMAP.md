# Day6 Agent — 能力缺口与优化路线图

## 当前状态

项目是一个可运行的 Agent 雏形，具备：LLM 对话（流式）、工具调用（Skill 可插拔）、RAG 检索（关键词评分）、三层记忆（内存）、可观测性追踪面板、Vue 3 前端。

以下列出当前已知的能力缺口，按优先级排序，后续逐步实现。

---

## 一、用户评估（6 项）

### 1. 消息没有持久化存储

**现状：** `ShortTermMemory` 使用内存 `ArrayList`，F5 刷新或服务重启后全部丢失。

**目标：** 聊天记录存数据库（MySQL/PostgreSQL），按会话 ID 隔离，支持历史回看。

**严重程度：** 🔴 高 | **涉及模块：** `ShortTermMemory`、新增 Repository 层

---

### 2. 缺少每日记忆抽取

**现状：** `LongTermMemory.extractAndStore()` 只做简单关键词匹配（“我叫”、“我是”），没有定时任务对每日对话做摘要提取。

**目标：** 定时任务（每日/每小时）用 LLM 对当天对话做摘要，提取用户偏好、事实、待办，存入长期记忆。

**严重程度：** 🟡 中 | **涉及模块：** `LongTermMemory`、新增 Scheduler

---

### 3. Memory 三层都没有持久化

**现状：** `ShortTermMemory`（`ArrayList`）、`LongTermMemory`（`DocumentStore` 内存 Map）、`WorkingMemory`（`ConcurrentHashMap`）全部在内存中，重启清零。

**目标：** 三层记忆分别落库，短期记记忆关联会话、长期记忆关联用户、工作记忆关联任务。

**严重程度：** 🔴 高 | **涉及模块：** `memory/` 全部、新增持久层

---

### 4. RAG 实现过于简单

**现状：** 关键词分词 + TF 评分检索，没有 Embedding 模型将文本向量化，没有向量数据库做相似度检索。

**目标：** 引入 Embedding API（`text-embedding-3-small` 或本地 BGE 模型）+ 向量数据库（Redis Stack / PGVector / Milvus），支持语义检索。

**严重程度：** 🟡 中 | **涉及模块：** `rag/`

---

### 5. 多 Agent 编排 + Human-in-the-loop 未实现

**现状：** Day5 的 jchatdemo 项目中有 Orchestrator + Specialist 模式（搜索专家/工具专家 + 审批），但 day6-agent 项目没有搬过来。

**目标：** 将多 Agent 编排和 Human-in-the-loop 审批集成到本项目中。

**严重程度：** 🟡 中 | **涉及模块：** 新增 `agent/` 或 `orchestrator/` 包

---

### 6. 项目文档缺失

**现状：** 没有 README、没有模块说明、没有架构图，后续接手者无法理解项目结构。

**目标：** 编写项目 README（启动方式、技术栈、目录结构说明），各模块包下加 `package-info.java` 或 README。

**严重程度：** 🔴 高 | **涉及模块：** 全项目

---

## 二、补充评估（10 项）

### 7. 没有会话隔离

**现状：** `ShortTermMemory` 是全局单例 Bean，所有前端连接共享同一份对话上下文。A 用户的对话会串到 B 用户。

**目标：** 引入会话 ID（`sessionId`），前端生成 UUID 带在请求中，后端按 sessionId 隔离短期记忆。

**严重程度：** 🔴 高 | **涉及模块：** `ChatController`、`ShortTermMemory`、前端

---

### 8. 没有认证鉴权

**现状：** `/api/chat/stream` 无任何认证，知道端口就能调。

**目标：** 引入 Spring Security + JWT / API Key 认证。

**严重程度：** 🟡 中（Demo 阶段） / 🔴 高（生产阶段） | **涉及模块：** 新增 Security 配置

---

### 9. 没有流控 / 限流

**现状：** LLM API 调用无频率限制，恶意或 bug 循环会迅速耗尽 token 配额。

**目标：** 引入 Rate Limiter（Bucket4j / Guava RateLimiter），按用户/IP 限制每分钟请求数。

**严重程度：** 🟡 中 | **涉及模块：** `ChatController`、新增 Filter/Interceptor

---

### 10. System Prompt 硬编码

**现状：** 工具使用规则写死在 `ChatController` 的字符串里，修改 prompt 要改代码、编译、部署。

**目标：** Prompt 外部化到配置文件或数据库，支持热更新。

**严重程度：** 🟡 中 | **涉及模块：** `ChatController`、`application.yml`

---

### 11. 缺少统一的日志体系

**现状：** 项目中使用 `System.out.println` 打印调试信息，没有统一日志级别（DEBUG/INFO/WARN/ERROR）、没有格式、没有输出目标配置。

**目标：** 全部替换为 SLF4J + Logback，按级别输出，`application.yml` 控制日志级别。

**严重程度：** 🟡 中 | **涉及模块：** 全项目

---

### 12. 没有错误重试机制

**现状：** LLM 调用失败直接抛异常给前端，没有 `@Retryable` 或 fallback 策略。

**目标：** LLM 调用加 Spring Retry 重试（3 次，指数退避），工具调用失败有降级策略。

**严重程度：** 🟢 低（Demo 阶段） / 🟡 中（生产阶段） | **涉及模块：** `ChatController`

---

### 13. 没有 Tool 权限分级

**现状：** 所有工具对 LLM 平等开放，没有“只读/需审批/禁止”的分级。例如 `noteToSelf` 和 `calculator` 应该是只读安全的，但如果有 `deleteFile` 工具就需要审批。

**目标：** 工具分为 AUTO（自动执行）、CONFIRM（需用户确认）、FORBIDDEN（LLM 不可见）。

**严重程度：** 🟢 低 | **涉及模块：** `Skill` 接口、`SkillRegistry`

---

### 14. 流式输出无取消机制

**现状：** 用户关闭浏览器页面后，后端 `executor` 线程仍在等 LLM 流式返回，浪费 token。

**目标：** `SseEmitter` 的 `onCompletion` / `onTimeout` 回调中取消 `Subscription`。

**严重程度：** 🟢 低 | **涉及模块：** `ChatController`

---

### 15. 没有环境配置分离

**现状：** `application.yml` 只有一个文件，API Key 直接写在其中，无法区分 dev/prod 环境。

**目标：** 拆分为 `application.yml`（公共）+ `application-dev.yml` + `application-prod.yml`，敏感信息用环境变量。

**严重程度：** 🟡 中 | **涉及模块：** `src/main/resources/`

---

### 16. 缺少健康检查端点

**现状：** 没有 `/actuator/health`，无法监控服务是否存活。

**目标：** 引入 Spring Boot Actuator，暴露 health、metrics 端点。

**严重程度：** 🟡 中 | **涉及模块：** `pom.xml`、`application.yml`

---

## 三、实施优先级

### 第一优先（架构基础）

| # | 任务 | 解决方案 | 预计影响 |
|---|------|---------|---------|
| 1 | 消息持久化 | 引入数据库，会话级存储 | 解决一次性聊天问题 |
| 3 | Memory 持久化 | 短期记忆/长期记忆/工作记忆全部落库 | 重启不丢数据 |
| 7 | 会话隔离 | 前端生成 sessionId，后端按 ID 隔离 | 多用户可用 |

### 第二优先（可维护性）

| # | 任务 | 解决方案 | 预计影响 |
|---|------|---------|---------|
| 6 | 项目文档 | README + 模块说明 + 架构图 | 后续可维护 |
| 11 | 日志体系 | System.out → SLF4J + Logback | 可调试可监控 |
| 10 | Prompt 配置化 | 外部化到 yml 或数据库 | 改 prompt 不需要重新部署 |

### 第三优先（能力提升）

| # | 任务 | 解决方案 | 预计影响 |
|---|------|---------|---------|
| 4 | 向量 RAG | Embedding + VectorStore | 检索准确性大幅提升 |
| 5 | 多 Agent + HITL | 迁移 Day5 模式 | Agent 能力升级 |
| 2 | 每日记忆抽取 | 定时 LLM 摘要 | 长期记忆更丰富 |
| 8 | 认证鉴权 | Spring Security | 安全可用 |

### 第四优先（生产加固）

| # | 任务 | 解决方案 |
|---|------|---------|
| 9 | 流控限流 | Rate Limiter |
| 12 | 错误重试 | Spring Retry |
| 13 | Tool 权限分级 | AUTO/CONFIRM/FORBIDDEN |
| 14 | 取消机制 | SseEmitter 回调取消 Subscription |
| 15 | 环境配置分离 | application-{profile}.yml |
| 16 | 健康检查 | Spring Boot Actuator |

---

## 四、当前项目结构速查

```
day6-agent/
├── pom.xml
├── ROADMAP.md                          ← 本文件
├── src/main/java/com/agent/
│   ├── AgentApplication.java           # Spring Boot 入口
│   ├── controller/ChatController.java  # SSE 流式接口 + @Resource 注入
│   ├── skill/                          # 可插拔 Skill 系统
│   │   ├── Skill.java                  # 接口
│   │   ├── SkillRegistry.java          # 自动发现 + 启用/禁用
│   │   └── impl/CalculatorSkill.java, TranslationSkill.java, DiceSkill.java
│   ├── rag/                            # RAG 检索
│   │   ├── DocumentStore.java          # 关键词检索 + TF 评分
│   │   ├── IngestionService.java       # 文件读取 → 切片 → 入库
│   │   └── RagTool.java                # @Tool 暴露给 LLM
│   ├── memory/                         # 三层记忆
│   │   ├── ShortTermMemory.java        # 滑动窗口 + 摘要压缩
│   │   ├── LongTermMemory.java         # 用户画像提取/检索
│   │   ├── WorkingMemory.java          # noteToSelf / readMyNotes
│   │   └── MemoryTools.java            # @Tool 暴露给 LLM
│   └── trace/TracingService.java       # 全链路追踪 + 工具调用计数
├── src/main/resources/application.yml
└── frontend/                           # Vue 3 + Vite
    ├── src/App.vue                     # 布局：聊天 + 追踪面板
    ├── src/components/ChatView.vue     # 聊天界面 + SSE 流式
    └── src/components/TracePanel.vue   # 追踪面板
```
