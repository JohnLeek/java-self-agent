# Day 5：多 Agent 编排 + Human-in-the-loop + 前端

## 学习目标

实现 Orchestrator + 3 Specialist 多 Agent 编排，加入人工审批节点，理解 SSE 流式前端对接。

## 多 Agent 编排架构

```
用户提问
    ↓
Orchestrator Agent（Plan 规划 + Summarize 汇总）
    ├── Search Specialist  → RagTool（pgvector 语义检索）
    ├── Tool Specialist    → Skills + MCP 工具
    └── Memory Specialist  → 记忆读写
    ↓
流式输出最终回答（SSE）
```

## SpecialistAgent — 专家角色定义

`src/main/java/com/agent/agent/SpecialistAgent.java`：

```java
public class SpecialistAgent {
    private final String name;
    private final ChatClient chatClient;

    public SpecialistAgent(String name, ChatClient.Builder builder,
                           String systemPrompt, FunctionCallback... tools) {
        this.name = name;
        this.chatClient = builder
            .defaultSystem(systemPrompt)
            .defaultTools(tools)
            .build();
    }

    public String delegate(String task) {
        return chatClient.prompt().user(task).call().content();
    }
}
```

## AgentModeConfig — Bean 配置

`src/main/java/com/agent/config/AgentModeConfig.java`：

```java
@Bean
SpecialistAgent searchAgent(...) {
    return new SpecialistAgent("搜索专家", ...,
        "你是搜索专家。用 search 工具在知识库中检索，基于结果回答。",
        importedRegistry.toToolCallbacks(DEFAULT_USER_ID),  // MCP 工具
        ragTool);                                           // RAG 工具
}

@Bean
SpecialistAgent toolAgent(...) {
    return new SpecialistAgent("工具专家", ...,
        "你是工具专家。用所有可用的工具完成任务，包括内部工具和外部 MCP 工具。",
        importedRegistry.toToolCallbacks(DEFAULT_USER_ID),  // MCP 工具
        calculator, dice, translation);                     // 内置 Skill
}

@Bean
SpecialistAgent memoryAgent(...) {
    return new SpecialistAgent("记忆专家", ...,
        "你是记忆专家。用 noteToSelf/readMyNotes/getUserProfile 管理用户信息和笔记。",
        memoryTools);
}
```

每个专家有独立的 system prompt 和工具集，LLM 的行为被精确限定在职责范围内。

## OrchestratorAgent — Plan-Execute-Summarize

`src/main/java/com/agent/agent/OrchestratorAgent.java`：

### Step 1: Plan（规划）

```java
private String plan(String message) {
    return planner.prompt()
        .system("判断需要哪些专家，只输出标签（逗号分隔）。" +
                "SEARCH(查知识库) TOOL(计算/翻译/脚本) MEMORY(笔记/用户)。" +
                "都不需要输出NONE。")
        .user(message).call().content().trim();
}
```

### Step 2: Execute（执行）

```java
if (plan.contains("SEARCH")) {
    // 中断检查
    if (executionCtrl.isStopped(sessionId)) { sink.complete(); return; }
    // SSE 推送给前端
    emit(sink, "agent_call", "🔍 调用搜索专家", "在知识库中语义检索...");
    // 委托给搜索专家
    lastResult = searchAgent.delegate(searchTask);
}

if (plan.contains("TOOL")) { ... }
if (plan.contains("MEMORY")) { ... }
```

### Step 3: Summarize（汇总）

```java
String summaryPrompt = "你是回答汇总助手。不要编造信息。";
if (profileContext != null) summaryPrompt += "\n" + profileContext;
if (memoryContext != null) summaryPrompt += "\n【对话上下文】\n" + memoryContext;

summarizer.prompt()
    .system(summaryPrompt)
    .user(context.toString())
    .stream().content()
    .doOnNext(c -> sink.next(c))    // 流式推送
    .doOnComplete(() -> sink.complete());
```

## ExecutionController — 支持用户中断

`src/main/java/com/agent/agent/ExecutionController.java`：

```java
@Component
public class ExecutionController {
    private final ConcurrentHashMap<String, Boolean> stopFlags = new ConcurrentHashMap<>();

    public void requestStop(String sessionId) { stopFlags.put(sessionId, true); }
    public boolean isStopped(String sessionId) { ... }
    public void clear(String sessionId) { stopFlags.remove(sessionId); }
}
```

用户在每一步执行前都能中断 Agent。

## Human-in-the-loop 审批流

### ApprovalController

`src/main/java/com/agent/controller/ApprovalController.java`：

```
Agent 调用危险工具
    → ApprovalController.requestApproval(action)
    → 创建 CompletableFuture，阻塞等待 60s
    → SSE 推送 approval_required 事件到前端
    → 前端弹出确认卡片
    → 用户点击"确认" → POST /api/approval/{id}/approve
    → 用户点击"拒绝" → POST /api/approval/{id}/reject
    → Future 完成，Agent 继续或跳过
```

### 审批粒度

通过 OrchestratorTools 的 `requestApproval` 工具，Orchestrator 在调用危险操作前先请求审批：

```java
@Tool(description = "在执行危险操作前请求用户批准")
public String requestApproval(
    @ToolParam(description = "要执行的操作描述") String action) {
    return ApprovalController.requestApproval(action);
}
```

## 前端 SSE 对接

### 后端 SSE 事件类型

| 事件 | 含义 | 前端行为 |
|------|------|---------|
| `session` | 新会话 ID | 更新 URL |
| `step` | Agent 推理步骤（JSON） | 思考面板实时展示 |
| `chunk` | 流式文本片段 | 打字机效果追加到回答气泡 |
| `trace` | 追踪数据 | 追踪面板更新耗时/Token |
| `done` | 对话完成 | 停止流式动画 |
| `error` | 错误信息 | 红色错误提示 |

### ChatView.vue — 核心组件

`frontend/src/components/ChatView.vue`：

```javascript
// SSE 事件消费
const eventSource = new EventSource(url);

eventSource.addEventListener('step', (e) => {
    // 解析 STEP:{"phase":"agent_call","title":"🔍 调用搜索专家","content":"..."}
    // 在思考面板中实时展示当前执行步骤
});

eventSource.addEventListener('chunk', (e) => {
    // 追加文本到当前回答气泡（打字机效果）
    currentAnswer.value += e.data;
});

eventSource.addEventListener('trace', (e) => {
    // 更新追踪面板：总耗时、工具调用次数
});

eventSource.addEventListener('done', () => {
    // 完成，停止动画，折叠思考过程
});
```

### 三大面板

| 面板 | 组件 | 功能 |
|------|------|------|
| 聊天主界面 | `ChatView.vue` | 消息气泡、Markdown 渲染、流式输出、审批卡片 |
| 技能管理 | `SkillPanel.vue` | 导入/启用/禁用 Skill、MCP 模板市场 |
| 追踪面板 | `TracePanel.vue` | 调用链可视化、Token 统计、耗时分布 |

## 模式切换

```yaml
agent:
  mode: multi   # 多 Agent 编排
  # mode: single  # 单 Agent 直接推理
```

`ChatService.stream()` 根据配置分发：

```java
if ("multi".equals(agentMode)) {
    return orchestratorAgent.process(message, sessionId, memCtx, profile);
}
// 否则走单 Agent 模式
return buildChatClient().prompt()...;
```

## 关键文件

| 文件 | 作用 |
|------|------|
| `agent/OrchestratorAgent.java` | Plan-Execute-Summarize 编排 |
| `agent/SpecialistAgent.java` | 专家角色定义 |
| `agent/OrchestratorTools.java` | 指挥者工具（委托、审批） |
| `agent/ExecutionController.java` | 用户中断控制 |
| `config/AgentModeConfig.java` | 三个 Specialist Bean 配置 |
| `controller/ChatController.java` | SSE 流式接口 |
| `controller/ApprovalController.java` | Human-in-the-loop 审批 |
| `frontend/src/components/ChatView.vue` | 前端聊天界面 |
| `frontend/src/components/TracePanel.vue` | 追踪面板 |

## 验收标准

- [x] 前端聊天界面可对话，流式输出打字机效果
- [x] 推理面板可视化 Plan → Execute → Summarize 过程
- [x] Human-in-the-loop：敏感操作弹确认框，确认后执行，拒绝则跳过
- [x] 用户可随时点击"停止"中断 Agent 执行
- [x] Single / Multi 模式一键切换

## 动手实验

```bash
# 1. 启动前端
cd frontend && npm install && npm run dev

# 2. 打开 http://localhost:3000

# 3. 测试多 Agent 流程
# 问："帮我查一下知识库里关于 Spring AI 的内容，顺便记个笔记"

# 4. 观察推理面板：
# → Orchestrator 规划：SEARCH, MEMORY
# → 搜索专家：检索知识库
# → 记忆专家：noteToSelf 记录
# → 汇总回答

# 5. 切换到 Single 模式对比行为
```
