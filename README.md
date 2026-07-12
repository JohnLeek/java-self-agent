# Day6 Agent — Java AI Agent 全栈项目

一个面向 **初学者和 Java 技术栈程序员** 的完整 AI Agent 开发示例，7 天从零搭建生产级智能助手。

基于 Spring Boot 3.3 + Spring AI 1.0.0-M6 + pgvector + Vue 3，覆盖 Agent 开发的全部核心知识点。

## 项目定位

这不是一个玩具 Demo，而是一个 **教学导向的生产级 Agent 应用**。每个模块都可以独立学习，组合起来就是一个完整的智能助手系统。

- 如果你是初学者：跟着代码和注释理解 Agent 的每个组成部分
- 如果你是 Java 后端：这里展示了如何用熟悉的 Spring 生态构建 AI 应用
- 如果你想深入 Agent：Skill、MCP、RAG、Memory、可观测性，每个模块都可以单独深挖

详细的 7 天学习路线见：[AI-Agent-学习路线.md](./AI-Agent-学习路线.md)

## 核心能力

| 模块 | 说明 | 状态 |
|------|------|:----:|
| **Skill 可插拔系统** | 内置技能（计算器、翻译、骰子）+ 外部技能热插拔（HTTP/Script/MCP） | ✅ |
| **三层记忆体系** | 短期记忆（Token 滑动窗口 + 摘要压缩）+ 长期记忆（用户画像）+ 工作记忆（Scratchpad） | ✅ |
| **MCP 协议集成** | 接入 8 种 MCP Server 模板（文件系统、GitHub、PostgreSQL、Brave 搜索等），自建 MCP Client 管理生命周期 | ✅ |
| **Agentic RAG** | 文档上传 → 切片 → BGE 向量化 → pgvector 语义检索 → 带相似度评分的引用回答 | ✅ |
| **多 Agent 编排** | Orchestrator + 3 Specialist（Search/Tool/Memory），Plan-Execute-Summarize 循环 | ✅ |
| **Human-in-the-loop** | 敏感操作暂停等人工确认，支持审批流（确认/拒绝），60s 超时保护 | ✅ |
| **全链路可观测** | 每一步推理、工具调用记录追踪，前端可视化调用链和 Token 消耗 | ✅ |
| **SSE 流式输出** | 实时推送思考步骤、工具调用、文本生成，前端可视化展示 Agent "思考过程" | ✅ |
| **ReAct 推理模式** | Plan → Execute → Observe 循环，支持单 Agent 和多 Agent 两种模式自由切换 | ✅ |
| **前端聊天界面** | Vue 3 聊天 UI + Markdown 渲染 + 推理面板 + 技能管理面板 + 追踪面板 | ✅ |

## 技术栈

| 层 | 技术 | 用途 |
|----|------|------|
| 框架 | Spring Boot 3.3.0 | 整体后端 |
| AI | Spring AI 1.0.0-M6 | LLM 调用、Tool、RAG、Advisors |
| LLM | DeepSeek Chat / DeepSeek V4 | 通过 OpenAI 兼容 API 接入 |
| 向量数据库 | pgvector (PostgreSQL 16) | 文档向量存储 + HNSW 近似检索 |
| 嵌入模型 | BGE-large-zh-v1.5 (本地部署) | 中文文本向量化（1024 维） |
| MCP | 自建 Client + 8 个 Server 模板 | 工具标准化接入 |
| 数据库迁移 | Flyway | 版本化管理数据库 Schema |
| 前端 | Vue 3 + Vite + Marked | AI 生成聊天 UI |
| 部署 | Docker Compose | 一键启动 pgvector + 后端 + 前端 |

## 项目结构

```
day6-agent/
├── src/main/java/com/agent/
│   ├── AgentApplication.java          # Spring Boot 入口
│   ├── agent/                          # Agent 编排
│   │   ├── OrchestratorAgent.java      # 指挥者：Plan-Execute-Summarize
│   │   ├── SpecialistAgent.java        # 专家：Search/Tool/Memory 三个角色
│   │   ├── OrchestratorTools.java      # 指挥者可用工具（委托、审批）
│   │   └── ExecutionController.java    # 执行中断控制（停止生成）
│   ├── skill/                          # 技能系统
│   │   ├── Skill.java                  # 技能接口
│   │   ├── SkillRegistry.java          # 技能注册中心
│   │   ├── impl/                       # 内置技能
│   │   │   ├── CalculatorSkill.java    # 表达式计算器（安全沙箱）
│   │   │   ├── TranslationSkill.java   # LLM 翻译
│   │   │   └── DiceSkill.java          # 随机骰子
│   │   └── external/                   # 外部技能
│   │       ├── ImportedSkillRegistry.java  # 外部技能注册（TOOL/MCP/PROMPT）
│   │       ├── ExternalSkillCallback.java  # HTTP/Script 工具适配器
│   │       ├── ScriptExecutor.java         # 安全脚本执行沙箱
│   │       ├── SkillRouter.java            # PROMPT 技能路由匹配
│   │       └── McpTemplate.java            # MCP Server 模板市场
│   ├── memory/                         # 记忆系统
│   │   ├── ShortTermMemory.java        # 短期：Token 窗口 + 摘要压缩
│   │   ├── LongTermMemory.java         # 长期：用户画像提取与检索
│   │   ├── WorkingMemory.java          # 工作：Scratchpad 笔记
│   │   └── MemoryTools.java            # Agent 可调用的记忆工具
│   ├── mcp/                            # MCP 协议
│   │   ├── McpClientManager.java       # MCP Client 生命周期管理
│   │   └── McpToolCallback.java        # MCP 工具 → Spring AI FunctionCallback
│   ├── rag/                            # RAG 检索增强生成
│   │   ├── IngestionService.java       # 文档摄取（旧版）
│   │   ├── DocumentStore.java          # 内存关键词搜索（旧版）
│   │   ├── RagTool.java               # Agent 可调用的检索工具
│   │   └── vector/                     # 向量 RAG
│   │       ├── RagVectorService.java   # pgvector 向量检索服务
│   │       └── EmbeddingClient.java    # BGE 嵌入服务客户端
│   ├── controller/                     # REST API
│   │   ├── ChatController.java         # SSE 流式聊天 + 会话管理
│   │   ├── ApprovalController.java     # Human-in-the-loop 审批
│   │   ├── RagController.java          # 文档上传与知识库管理
│   │   └── SkillController.java        # 技能 CRUD + 导入
│   ├── dao/                            # JPA 实体
│   ├── repository/                     # Spring Data JPA 仓库
│   ├── service/                        # 业务服务
│   │   ├── ChatService.java            # 对话编排（单/多 Agent 模式）
│   │   └── SessionService.java         # 会话管理
│   ├── trace/                          # 可观测性
│   │   └── TracingService.java         # 调用链追踪（Span 模型）
│   └── config/
│       └── AgentModeConfig.java        # Agent 模式配置（single/multi）
├── src/main/resources/
│   ├── application.yml                 # 应用配置（LLM/DB/Agent 模式）
│   └── db/migration/                   # Flyway 数据库迁移
│       ├── V1__init_schema.sql         # 初始化表结构 + pgvector 扩展
│       ├── V2__add_session_summary.sql # 会话摘要字段
│       ├── V3__imported_skills.sql     # 外部技能表
│       ├── V4__rag_vector.sql          # 向量维度调整 + HNSW 索引
│       └── V5__agent_traces.sql        # Agent 追踪表
├── frontend/                           # Vue 3 前端
│   ├── src/
│   │   ├── App.vue                     # 根布局（侧栏 + 三面板）
│   │   └── components/
│   │       ├── ChatView.vue            # 聊天主界面（SSE 流式 + 审批卡片）
│   │       ├── SkillPanel.vue          # 技能管理面板（导入/启用/禁用）
│   │       └── TracePanel.vue          # 追踪面板（调用链 + Token 统计）
│   ├── vite.config.js
│   └── package.json
├── embedding_server.py                 # BGE 嵌入服务（Python，端口 9999）
├── docker-compose.yml                  # pgvector 一键启动
└── pom.xml                             # Maven 依赖管理
```

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- Node.js 18+
- Python 3.10+（用于嵌入服务）
- Docker（用于 pgvector）

### 1. 启动 PostgreSQL + pgvector

```bash
docker-compose up -d
```

### 2. 启动 BGE 嵌入服务

```bash
pip install flask torch transformers
python embedding_server.py
# 服务启动在 http://localhost:9999
# 首次运行会自动下载 BAAI/bge-large-zh-v1.5 模型
```

### 3. 配置 LLM API Key

```bash
export LLM_API_KEY=your_deepseek_api_key
```

或直接修改 `src/main/resources/application.yml` 中的 `api-key`。

### 4. 启动后端

```bash
mvn spring-boot:run
# 服务启动在 http://localhost:8080
```

### 5. 启动前端

```bash
cd frontend
npm install
npm run dev
# 开发服务器启动在 http://localhost:3000
# API 请求自动代理到 8080
```

### 6. 打开浏览器

访问 `http://localhost:3000`，开始和你的 Agent 对话。

### Agent 模式切换

在 `application.yml` 中修改：

```yaml
agent:
  mode: multi   # single = 单 Agent 直接推理, multi = Orchestrator + Specialist 编排
```

## 核心模块详解

### Skill 技能系统

技能是 Agent 的"能力插件"，支持三种类型：

**内置技能**（`@Tool` 注解，自动注册）：
- `CalculatorSkill` — 安全表达式计算器，递归下降解析，无 `eval` 风险
- `TranslationSkill` — 调用 LLM 进行中英文互译
- `DiceSkill` — 真随机数生成

**外部技能**（运行时热插拔，无需重启）：

| 类型 | 说明 | 示例 |
|------|------|------|
| PROMPT | Markdown 提示词注入，通过触发器关键词匹配激活 | 代码审查规范、特定领域知识 |
| TOOL | HTTP API 或 Script 脚本，参数自动映射 | 天气 API、自定义 Python 脚本 |
| MCP | 标准 MCP Server，自动发现工具列表 | 文件系统、GitHub、数据库 |

```java
// 技能接口
public interface Skill {
    String name();
    String description();
    boolean isEnabled();
    void setEnabled(boolean enabled);
}
```

### 记忆体系

```
┌─────────────────────────────────────────────┐
│              Agent Memory 体系                │
├───────────────┬───────────────┬──────────────┤
│   短期记忆      │   长期记忆     │   工作记忆    │
│   (会话级)     │   (用户级)     │   (任务级)    │
├───────────────┼───────────────┼──────────────┤
│ Token 滑动窗口 │ 用户画像提取   │ Scratchpad   │
│ LLM 摘要压缩   │ 关键词 + 向量  │ 笔记读写      │
│ 消息持久化     │ 跨会话检索     │ 中间结果暂存  │
└───────────────┴───────────────┴──────────────┘
```

- **短期记忆**：Token 预算管理（上下文的 50%），超限自动触发 LLM 摘要压缩
- **长期记忆**：从对话中自动提取用户偏好和背景信息，下次对话时注入上下文
- **工作记忆**：`noteToSelf` / `readMyNotes` 工具，给 Agent "思考但不行动"的空间

### MCP 协议集成

支持 8 种开箱即用的 MCP Server 模板：

| 模板 | 功能 | 实现 |
|------|------|------|
| filesystem | 文件读写、目录浏览 | `@modelcontextprotocol/server-filesystem` |
| memory | 持久化记忆系统 | `@modelcontextprotocol/server-memory` |
| github | Issues/PR/仓库操作 | `@anthropic/mcp-server-github` |
| sequential-thinking | 结构化思考链 | MCP 官方 |
| brave-search | Brave 搜索引擎 | MCP 社区 |
| playwright | 浏览器自动化 | MCP 社区 |
| fetch | HTTP 请求、网页抓取 | `mcp-server-fetch` |
| postgres | PostgreSQL 查询 | `mcp-server-postgres` |

MCP Server 通过 stdio 协议通信，Agent 启动时自动发现工具列表，无需硬编码。

### Agentic RAG

```
文档上传 → 切片（500字符/50重叠）
         → BGE-large-zh-v1.5 嵌入（1024维）
         → pgvector HNSW 索引

用户提问 → 查询嵌入 → 余弦相似度检索
         → 返回 Top-5 结果 + 相似度评分
         → Agent 基于检索结果回答（带来源引用）
```

- 支持格式：TXT、Markdown、PDF、Word
- 向量索引：HNSW（m=16, ef_construction=200），毫秒级检索
- 降级策略：BGE 服务不可用时自动回退伪嵌入，保证开发链路通畅

### 多 Agent 编排

```
用户提问
    ↓
Orchestrator Agent（规划 + 调度）
    ├── Search Specialist  → RagTool（检索知识库）
    ├── Tool Specialist    → Skills + MCP 工具
    └── Memory Specialist  → 记忆读写
    ↓
Orchestrator Agent（汇总 → 流式输出最终回答）
```

- **计划阶段**：LLM 分析用户意图，决定需要哪些专家
- **执行阶段**：按序调用专家，每步记录追踪，支持中断
- **摘要阶段**：汇总所有结果，注入记忆上下文，流式生成回答

通过 `agent.mode: single` 可切换为单 Agent 模式，所有工具直接注册到一个 ChatClient。

### Human-in-the-loop

敏感操作不会自动执行，而是暂停等待用户确认：

```
Agent 调用危险工具
    → ApprovalController 创建审批请求
    → SSE 推送 approval_required 事件
    → 前端弹出确认卡片
    → 用户点击"确认"或"拒绝"
    → Agent 继续或跳过
    → 60s 无响应自动超时拒绝
```

### 可观测性

每一步都有追踪记录，前端可视化：

```
┌─ [PLAN]  Orchestrator 分析意图
├─ [EXEC]  SearchAgent.searchKnowledgeBase("Spring AI MCP")
│           └─ 5 条结果, 耗时 2.3s
├─ [EXEC]  ToolAgent.calculate("3.14 * 2")
│           └─ 返回 6.28, 耗时 0.1s
└─ [SUMMARY] 生成最终回答
   总耗时: 8.5s | Token: 2341 | 工具调用: 2 次
```

## API 概览

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/chat/stream?message=&sessionId=` | SSE 流式对话（核心接口） |
| POST | `/api/chat/stop` | 停止当前生成 |
| POST | `/api/session` | 创建新会话 |
| GET | `/api/sessions` | 会话列表 |
| GET | `/api/sessions/{id}/messages` | 历史消息 |
| DELETE | `/api/sessions/{id}` | 删除会话 |
| GET | `/api/sessions/{id}/traces?round=N` | 获取追踪数据 |
| POST | `/api/rag/upload` | 上传文档到知识库 |
| GET | `/api/rag/sources` | 已索引文档列表 |
| DELETE | `/api/rag/doc?source=` | 删除文档 |
| POST | `/api/approval/{id}/approve` | 批准操作 |
| POST | `/api/approval/{id}/reject` | 拒绝操作 |
| GET | `/api/skills` | 技能列表 |
| POST | `/api/skills` | 导入技能 |
| PUT | `/api/skills/{id}/enable` | 启用技能 |
| DELETE | `/api/skills/{id}` | 卸载技能 |
| GET | `/api/skills/mcp-templates` | MCP 模板市场 |
| GET | `/api/health` | 健康检查 |

## SSE 事件类型

| 事件 | 说明 | 前端展示 |
|------|------|---------|
| `session` | 新会话 ID | 更新 URL |
| `step` | Agent 推理步骤 | 思考面板实时更新 |
| `chunk` | 流式文本片段 | 打字机效果追加 |
| `trace` | 追踪数据（耗时/Token/工具调用） | 追踪面板 |
| `done` | 对话完成 | 停止流式动画 |
| `error` | 错误信息 | 红色错误提示 |

## 数据库

- **PostgreSQL 16** + **pgvector** 扩展
- Flyway 管理 5 个版本的 Schema 迁移
- 表：users, sessions, messages, user_profiles, notes, documents, imported_skills, agent_traces
- 无外键约束（应用层保证一致性）
- 向量索引使用 HNSW 算法

## 进阶方向

完成 7 天学习后，可以继续深入：

- **A2A 协议** — Agent 间互操作标准，让不同框架的 Agent 互相调用
- **Graph Agent 编排** — 用状态机/图替代硬编码的 Orchestrator
- **Agent Eval 体系** — 单元级/任务级/质量级三层评估
- **安全加固** — Prompt Injection 防护、路径穿越拦截、工具沙箱
- **Voice Agent** — ASR → Agent → TTS 语音交互链路
- **Computer Use** — Agent 操作桌面 GUI，自动化无 API 的操作

## 参考资源

- [Building Effective Agents - Anthropic](https://docs.anthropic.com/en/docs/agents-and-tools) — Agent 设计圣经
- [Spring AI 官方文档](https://docs.spring.io/spring-ai/reference/)
- [MCP 协议规范](https://modelcontextprotocol.io/specification/)
- [MCP Servers 合集](https://github.com/modelcontextprotocol/servers)
- [pgvector 文档](https://github.com/pgvector/pgvector)
- [BGE 嵌入模型](https://huggingface.co/BAAI/bge-large-zh-v1.5)

## License

MIT
