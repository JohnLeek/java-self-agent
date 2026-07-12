# Day 6：Agent Memory 体系 + 可观测性

## 学习目标

设计结构化记忆系统（短期/长期/工作三层），接全链路追踪可视化每一步推理过程。

## 记忆体系架构

```
┌─────────────────────────────────────────────┐
│              Agent Memory 体系               │
├───────────────┬───────────────┬──────────────┤
│   短期记忆      │   长期记忆     │   工作记忆    │
│   (会话级)     │   (用户级)     │   (任务级)    │
├───────────────┼───────────────┼──────────────┤
│ Token 滑动窗口 │ 用户画像提取   │ Scratchpad   │
│ LLM 摘要压缩   │ 关键词 + 向量  │ 笔记读写      │
│ messages 表    │ user_profiles  │ notes 表     │
└───────────────┴───────────────┴──────────────┘
```

## 短期记忆：ShortTermMemory

`src/main/java/com/agent/memory/ShortTermMemory.java`：

### Token 预算管理

```java
@Component
public class ShortTermMemory {
    // 不同模型的上下文窗口
    private static final Map<String, Integer> MODEL_CONTEXT = Map.of(
        "deepseek-chat", 64_000, "deepseek-v4", 1_000_000,
        "gpt-4o", 128_000, "gpt-4o-mini", 128_000
    );

    // 初始化时计算 Token 预算 = 上下文的 50%
    public ShortTermMemory(...) {
        int ctx = MODEL_CONTEXT.getOrDefault(model, 64_000);
        this.tokenBudget = (int)(ctx * 0.5);
    }
}
```

### 滑动窗口

```java
public String context(String sessionId) {
    List<Message> msgs = messageRepo.findBySessionIdOrderByCreatedAtAsc(sessionId);

    // 从最新消息往前累加 Token，不超过预算
    long tokenCount = 0;
    int cutoff = msgs.size();
    for (int i = msgs.size() - 1; i >= 0; i--) {
        tokenCount += estimateTokens(msgs.get(i).getContent());
        if (tokenCount > tokenBudget) { cutoff = i + 1; break; }
    }
    List<Message> recent = msgs.subList(cutoff, msgs.size());

    // 拼接：历史摘要 + 最近消息
    StringBuilder sb = new StringBuilder();
    if (!summary.isEmpty()) {
        sb.append("【历史对话摘要】\n").append(summary).append("\n\n");
    }
    sb.append("【最近对话】\n");
    for (Message m : recent) { ... }
    return sb.toString();
}
```

### 异步摘要压缩

```java
public void record(String sessionId, String userMsg, String agentMsg) {
    messageRepo.save(Message.user(sessionId, userMsg));
    messageRepo.save(Message.agent(sessionId, agentMsg));

    // Token 超过预算时异步压缩
    long currentTokens = estimateSessionTokens(sessionId);
    if (currentTokens > tokenBudget) {
        compressAsync(sessionId);
    }
}

private void compressAsync(String sessionId) {
    CompletableFuture.runAsync(() -> {
        // 取前半部分旧消息
        List<Message> oldMessages = all.subList(0, all.size() / 2);

        // LLM 摘要（不超过 200 字）
        String newSummary = summaryBuilder.build()
            .prompt()
            .system("你是一个对话摘要助手。" +
                    "从对话中提取关键信息：用户身份、偏好、讨论主题、重要决策、待办事项。" +
                    "只输出摘要内容，不超过200字。")
            .user("请摘要以下对话：\n\n" + conversation)
            .call().content();

        // 合并新旧摘要，持久化到 sessions.summary
        String merged = old.isEmpty() ? newSummary : old + "；" + newSummary;
        sessionRepo.findById(sessionId).ifPresent(s -> {
            s.setSummary(merged); sessionRepo.save(s);
        });
    });
}
```

### Token 估算算法

```java
// 中文字符 ~1.5 字/token，英文 ~4 字/token
private long estimateTokens(String text) {
    int chinese = 0, other = 0;
    for (char c : text.toCharArray()) {
        if (Character.UnicodeBlock.of(c) ==
                Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS)
            chinese++; else other++;
    }
    return chinese / 2 + other / 4;
}
```

## 长期记忆：LongTermMemory

`src/main/java/com/agent/memory/LongTermMemory.java`：

```java
@Component
public class LongTermMemory {
    // 从用户消息中提取事实（关键词匹配）
    // 匹配模式："我叫"、"我是"、"我喜欢"、"我在"等
    public void extractAndStore(Long userId, String userMsg, Long sourceMessageId) {
        // 提取事实 → 创建 UserProfile → 保存到 user_profiles 表
    }

    // 检索最近 10 条用户画像
    public String retrieve(Long userId) -> String {
        List<UserProfile> profiles = repo.findByUserIdOrderByCreatedAtDesc(userId);
        // 拼接成 "已知用户信息：\n- 姓名：xxx\n- 角色：xxx\n- ..."
    }
}
```

> 当前版本用关键词匹配提取。进阶方向：用 LLM 做语义提取，存向量库实现语义检索。

## 工作记忆：WorkingMemory + MemoryTools

`src/main/java/com/agent/memory/WorkingMemory.java`：

```java
@Component
public class WorkingMemory {
    // 每会话笔记（Scratchpad 模式）
    public void note(String sessionId, Long userId, String content) {
        noteRepo.save(new Note(sessionId, userId, content));
    }
    public String readAll(String sessionId) {
        // 读出所有笔记，编号列表
    }
}
```

`src/main/java/com/agent/memory/MemoryTools.java`：

```java
@Component
public class MemoryTools {
    // ThreadLocal 上下文，由 ChatController 在每次请求前设置
    private static final ThreadLocal<RequestContext> CTX = new ThreadLocal<>();

    @Tool(description = "记录一条工作笔记，用于记住执行过程中的关键信息")
    public String noteToSelf(
        @ToolParam(description = "笔记内容") String note) {
        TracingService.recordToolCall("noteToSelf");
        workingMemory.note(CTX.get().sessionId, CTX.get().userId, note);
        return "已记录笔记";
    }

    @Tool(description = "查看之前记录的所有笔记")
    public String readMyNotes() {
        TracingService.recordToolCall("readMyNotes");
        return workingMemory.readAll(CTX.get().sessionId);
    }

    @Tool(description = "获取当前用户的已知信息（偏好、背景等）")
    public String getUserProfile() {
        TracingService.recordToolCall("getUserProfile");
        return longTerm.retrieve(CTX.get().userId);
    }
}
```

> `noteToSelf` / `readMyNotes` 是 Anthropic 强烈推荐的模式 —— 给 Agent "思考但不行动"的空间。

## 可观测性：TracingService

`src/main/java/com/agent/trace/TracingService.java`：

### Span 模型

```java
public static class Trace {
    public final String userInput;
    public final Instant startTime;
    public final List<Span> spans;

    public Span addSpan(String type, String name) { ... }
    public long totalMs() { ... }
    public int toolCount() { ... }

    public static class Span {
        public String type;     // LLM / TOOL / AGENT_CALL / PLAN / SUMMARY
        public String name;     // 实体名称
        public String detail;   // 详细信息
        public long durationMs; // 耗时毫秒
    }
}
```

### 工具调用计数

```java
// 使用 static AtomicInteger（不是 ThreadLocal！）
// 因为 @Tool 方法在 Spring AI 内部线程执行，
// drainToolCalls() 在 Controller 线程执行，ThreadLocal 隔离会导致读不到
private static final AtomicInteger toolCounter = new AtomicInteger(0);

public static void recordToolCall(String toolName) {
    toolCounter.incrementAndGet();
}

public static int drainToolCalls() {
    return toolCounter.getAndSet(0);
}
```

### AgentTrace 持久化

`src/main/java/com/agent/dao/AgentTrace.java`：

```
agent_traces 表记录每一步：
- session_id / round / step_order
- step_type: PLAN / EXEC / AGENT_CALL / TOOL_CALL / SUMMARY
- agent_name: 搜索专家 / 工具专家 / 记忆专家
- tool_name: search / calculate / noteToSelf 等
- input / output / metadata(JSONB)
- status: RUNNING / DONE / FAILED / INTERRUPTED
```

### 前端追踪面板

`frontend/src/components/TracePanel.vue`：

```
┌─ [PLAN]  Orchestrator 分析意图           0.5s
├─ [AGENT] 搜索专家.searchKnowledgeBase()   2.3s
├─ [AGENT] 工具专家.calculate()             0.1s
├─ [AGENT] 记忆专家.noteToSelf()            0.1s
└─ [SUMMARY] 生成最终回答                   3.2s
   总耗时: 8.5s | Token: 2341 | 工具调用: 3 次
```

## 关键文件

| 文件 | 作用 |
|------|------|
| `memory/ShortTermMemory.java` | Token 预算 + 滑动窗口 + 摘要压缩 |
| `memory/LongTermMemory.java` | 用户画像提取与检索 |
| `memory/WorkingMemory.java` | Scratchpad 笔记持久化 |
| `memory/MemoryTools.java` | Agent 可调用的记忆工具 |
| `trace/TracingService.java` | 全链路追踪（Span 模型） |
| `dao/AgentTrace.java` | 追踪实体 |
| `db/migration/V2__add_session_summary.sql` | 会话摘要字段 |
| `db/migration/V5__agent_traces.sql` | 追踪表 |
| `frontend/src/components/TracePanel.vue` | 前端追踪可视化 |

## 验收标准

- [x] 短期记忆：对话超过 Token 预算后，旧消息自动压缩为摘要
- [x] 长期记忆：用户说"我叫张三"后，下次对话 Agent 能记得
- [x] 工作记忆：Agent 自主使用 noteToSelf 记录中间结果
- [x] 追踪面板：前端可查看每次对话的完整调用链和 token 消耗
- [x] 追踪表持久化：历史对话的追踪数据可回溯

## 动手实验

1. 连续对话 20+ 轮，观察摘要压缩日志
2. 告诉 Agent "我叫张三，我是 Java 程序员"，然后新建对话问"你知道我是谁吗"
3. 让 Agent 执行复杂任务（检索 + 计算 + 记笔记），观察追踪面板
4. 查看 `agent_traces` 表中的历史追踪记录
