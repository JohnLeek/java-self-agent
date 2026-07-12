# Day 1：项目骨架 + ReAct Agent + 推理模式

## 学习目标

跑通 Java 版「LLM + Function Calling」最小闭环，理解三大核心推理模式。

## 项目初始化

### Maven 依赖

当前项目 `pom.xml` 的核心依赖：

```xml
<!-- Spring Boot -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- Spring AI：OpenAI 兼容接口，对接 DeepSeek -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
    <version>1.0.0-M6</version>
</dependency>
```

### LLM 配置

`src/main/resources/application.yml`：

```yaml
spring:
  ai:
    openai:
      api-key: ${LLM_API_KEY}
      base-url: https://api.deepseek.com
      chat:
        options:
          model: deepseek-chat
          temperature: 0.7
```

> 通过环境变量 `LLM_API_KEY` 传入密钥，避免硬编码。

### 启动类

`src/main/java/com/agent/AgentApplication.java`：

```java
@SpringBootApplication
public class AgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }
}
```

## 对比本项目的两种 Agent 模式

本项目支持两种模式，通过 `application.yml` 的 `agent.mode` 切换：

### Single Agent 模式

所有工具直接注册到一个 ChatClient，LLM 自主选择调用哪个工具：

```
用户提问 → ChatClient（拥有全部工具）
              ├── 检索知识库？ → RagTool.search()
              ├── 计算？ → CalculatorSkill.calculate()
              ├── 翻译？ → TranslationSkill.translate()
              └── 记录笔记？ → MemoryTools.noteToSelf()
          → 流式返回最终回答
```

对应代码：`ChatService.java:170-178`

```java
// 单 Agent 模式
return buildChatClient().prompt()
        .system(buildSystemPrompt(sessionId, message) + promptSkills)
        .user(message)
        .stream()
        .content();
```

### Multi Agent 模式（Orchestrator + Specialist）

```
用户提问
    → OrchestratorAgent.plan()     // LLM 分析意图，决定调度策略
    → OrchestratorAgent.execute()  // 按序调用搜索专家/工具专家/记忆专家
    → OrchestratorAgent.summarize() // 汇总所有结果，流式输出
```

对应代码：`OrchestratorAgent.java:43-122`

```java
public Flux<String> process(String message, String sessionId,
        String memoryContext, String profileContext) {
    return Flux.create(sink -> {
        // Step 1: Plan — 判断需要哪些专家
        String plan = plan(message);  // 输出 "SEARCH,TOOL" 等标签

        // Step 2: Execute — 按序调用专家
        if (plan.contains("SEARCH")) {
            String result = searchAgent.delegate(searchTask);
            context.append("【知识库检索结果】\n").append(result);
        }
        if (plan.contains("TOOL")) {
            String toolResult = toolAgent.delegate(toolTask);
            context.append("【工具执行结果】\n").append(toolResult);
        }

        // Step 3: Summarize — 汇总回答
        summarizer.prompt()
            .system(summaryPrompt)
            .user(context.toString())
            .stream().content()
            .doOnNext(c -> sink.next(c))
            .doOnComplete(() -> sink.complete());
    });
}
```

## 三种推理模式对比

理解 System Prompt 如何塑造 Agent 行为：

| 推理模式 | System Prompt 关键指令 | 项目对应 |
|---------|----------------------|---------|
| **ReAct** | "先 Think 分析当前状态，再 Action 选择工具，等待 Observation 后继续 Think..." | Multi Agent 模式默认行为 |
| **Chain-of-Thought** | "Let's think step by step. 逐步推理，每一步都写下来，最后给出答案。" | Single Agent 模式下不加工具 |
| **ReAct + Self-Reflection** | "每次工具调用后，检查结果是否满足需求。不满足则换策略重试，最多 3 次。" | 检查 RAG 相似度评分决定是否重搜 |

### 体验不同模式

修改 `application.yml`：

```yaml
agent:
  mode: single   # 单 Agent 直接推理
  # mode: multi  # 多 Agent 编排推理
```

## 消息拼装机制

理解一轮完整对话的消息序列：

```
System Prompt（Agent 角色定义 + 工具使用规则）
    ↓
User Message（用户输入）
    ↓
Assistant(tool_call: search, args: {query: "Spring AI MCP"})
    ↓
Tool(result: "检索到 5 条结果，相似度 0.82...")
    ↓
Assistant(tool_call: calculate, args: {expression: "3.14*2"})
    ↓
Tool(result: "6.28")
    ↓
Assistant(final_answer: "根据知识库检索和计算结果...")
```

Spring AI 自动管理这个消息序列，`ChatMemory` 接口负责持久化。

## 关键文件

| 文件 | 作用 |
|------|------|
| `AgentApplication.java` | Spring Boot 入口 |
| `pom.xml` | Maven 依赖管理 |
| `application.yml` | LLM 配置、Agent 模式切换 |
| `ChatService.java` | 对话编排，单/多 Agent 模式分发 |
| `OrchestratorAgent.java` | Plan-Execute-Summarize 循环 |
| `AgentModeConfig.java` | 三个 Specialist Bean 配置 |

## 验收标准

- [x] 项目能启动，通过 `mvn spring-boot:run` 运行
- [x] Single Agent 模式能自主决定调哪个工具
- [x] Multi Agent 模式能走通 Plan → Execute → Summarize
- [x] 多轮对话上下文记忆（短期记忆窗口）
- [x] 能说清 ReAct、CoT、Self-Reflection 三种模式的区别

## 快速验证

```bash
# 1. 启动 pgvector
docker-compose up -d

# 2. 启动后端
export LLM_API_KEY=your_key
mvn spring-boot:run

# 3. 测试对话
curl -X POST "http://localhost:8080/api/session"
curl "http://localhost:8080/api/chat/stream?message=今天几号&sessionId=xxx"
```
