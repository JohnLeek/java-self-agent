# Day 3：MCP（Model Context Protocol）全链路

## 学习目标

理解 MCP 协议的核心概念，掌握如何接入外部 MCP Server，理解"工具标准化"的价值。

## MCP 核心概念

```
┌─────────────────────┐          ┌──────────────────────┐
│    Day6 Agent        │   stdio  │    MCP Server         │
│  ┌───────────────┐   │ ◄──────► │  (npx / uvx 子进程)   │
│  │ McpClientManager│  │          │  ┌──────────────────┐ │
│  │ - connect()    │   │          │  │ listTools()       │ │
│  │ - listTools()  │   │          │  │ callTool()        │ │
│  │ - callTool()   │   │          │  │ listResources()   │ │
│  │ - disconnect() │   │          │  └──────────────────┘ │
│  └───────────────┘   │          └──────────────────────┘
└─────────────────────┘
```

- **MCP Client**：Agent 侧，连接 MCP Server，自动发现工具
- **MCP Server**：工具提供方，暴露 tools / resources / prompts
- **通信方式**：stdio（本地进程通信）
- **工具发现**：Agent 启动时自动发现，新增工具零代码改动

## 项目实现

### McpClientManager

`src/main/java/com/agent/mcp/McpClientManager.java`：

```java
@Component
public class McpClientManager {
    private final ConcurrentHashMap<Long, McpSyncClient> clients = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, List<FunctionCallback>> callbacksCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Boolean> failedCache = new ConcurrentHashMap<>();
    private static final long TIMEOUT_SECONDS = 10;

    public List<FunctionCallback> connectAndDiscover(
            Long skillId, String command, List<String> args, Map<String, String> env) {

        // 缓存命中
        if (callbacksCache.containsKey(skillId)) return callbacksCache.get(skillId);
        // 已知失败不重试
        if (failedCache.containsKey(skillId)) return List.of();

        // 超时保护，不阻塞启动
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<List<FunctionCallback>> future = executor.submit(() -> {
            // 启动 stdio 传输
            StdioClientTransport transport = new StdioClientTransport(
                ServerParameters.builder(command).args(args).env(env).build(), JSON_MAPPER);
            McpSyncClient client = McpClient.sync(transport).build();
            client.initialize();

            // 自动发现工具
            List<McpSchema.Tool> tools = client.listTools().tools();

            // 包装为 Spring AI FunctionCallback
            List<FunctionCallback> callbacks = new ArrayList<>();
            for (McpSchema.Tool tool : tools) {
                callbacks.add(new McpToolCallback("mcp_" + tool.name(), tool, client));
            }
            clients.put(skillId, client);
            return callbacks;
        });

        try {
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            failedCache.put(skillId, true);  // 失败缓存，不重试
            return List.of();
        }
    }

    @PreDestroy
    public void close() {
        clients.values().forEach(c -> { try { c.close(); } catch (Exception ignored) {} });
    }
}
```

### McpToolCallback

`src/main/java/com/agent/mcp/McpToolCallback.java`：

将 MCP 工具适配为 Spring AI 的 `FunctionCallback` 接口：

```java
public class McpToolCallback implements FunctionCallback {
    @Override
    public String getName() { return "mcp_" + tool.name(); }  // 前缀区分

    @Override
    public String getInputTypeSchema() {
        return tool.inputSchema().toString();  // 直接透传 MCP 的 JSON Schema
    }

    @Override
    public String call(String functionArguments) {
        TracingService.recordToolCall(getName());  // 追踪记录
        Map<String, Object> args = parseArguments(functionArguments);
        var result = client.callTool(
            new McpSchema.CallToolRequest(tool.name(), args));
        return extractText(result.content());  // 从 TextContent 提取文本
    }
}
```

## 内置 MCP 模板市场

`src/main/java/com/agent/skill/external/McpTemplate.java`：

| 模板 | 命令 | 功能 |
|------|------|------|
| filesystem | `npx @modelcontextprotocol/server-filesystem` | 文件读写、目录浏览 |
| memory | `npx @modelcontextprotocol/server-memory` | 持久化记忆系统 |
| github | `npx @anthropic/mcp-server-github` | Issues/PR/仓库操作 |
| sequential-thinking | `npx @anthropic/mcp-server-sequential-thinking` | 结构化思考链 |
| brave-search | `npx @anthropic/mcp-server-brave-search` | Brave 搜索引擎 |
| playwright | `npx @anthropic/mcp-server-playwright` | 浏览器自动化 |
| fetch | `uvx mcp-server-fetch` | HTTP 请求、网页抓取 |
| postgres | `uvx mcp-server-postgres` | PostgreSQL 数据库查询 |

前端 SkillPanel 点击模板即可安装，无需手动输入命令。

## 外部技能注册中的 MCP 集成

`ImportedSkillRegistry.java:74-96`：

```java
// MCP 类型：启动 MCP Server 并自动发现工具
List<ImportedSkill> mcpSkills = repo.findByUserIdAndTypeAndEnabledOrderByPriorityDesc(
    userId, "MCP", true);

for (ImportedSkill skill : mcpSkills) {
    Map<String, Object> def = json.readValue(skill.getSkillJson(), Map.class);
    Map<String, Object> server = (Map<String, Object>) def.get("server");

    String command = (String) server.get("command");
    List<String> args = (List<String>) server.get("args");
    Map<String, String> env = (Map<String, String>) server.get("env");

    List<FunctionCallback> mcpCallbacks =
        mcpClientManager.connectAndDiscover(skill.getId(), command, args, env);

    callbacks.addAll(mcpCallbacks);  // 与内置 Skill 统一注册
}
```

## 工具注册到 Specialist

`AgentModeConfig.java`：

```java
@Bean
SpecialistAgent searchAgent(...) {
    return new SpecialistAgent("搜索专家", ...,
        importedRegistry.toToolCallbacks(DEFAULT_USER_ID),  // MCP 工具也注入
        ragTool);
}

@Bean
SpecialistAgent toolAgent(...) {
    return new SpecialistAgent("工具专家", ...,
        importedRegistry.toToolCallbacks(DEFAULT_USER_ID),  // MCP 工具也注入
        calculator, dice, translation);
}
```

搜索专家和工具专家都获得 MCP 工具，LLM 根据任务自动选择。

## 关键文件

| 文件 | 作用 |
|------|------|
| `mcp/McpClientManager.java` | MCP Client 生命周期管理 |
| `mcp/McpToolCallback.java` | MCP 工具 → Spring AI FunctionCallback |
| `skill/external/ImportedSkillRegistry.java` | MCP Skill 解析与注册 |
| `skill/external/McpTemplate.java` | 内置 8 个 MCP 模板 |

## 验收标准

- [x] 前端一键安装 MCP 模板，Agent 自动发现工具
- [x] Agent 通过 MCP 文件系统工具读写文件
- [x] Agent 通过 MCP fetch 工具抓取网页
- [x] Agent 通过 MCP postgres 工具查询数据库
- [x] 禁用 MCP Skill 时自动断开连接、释放资源

## 动手实验

1. 在前端安装 `filesystem` 模板，让 Agent 列出项目目录
2. 安装 `postgres` 模板，让 Agent 查询 `agent_db` 的表结构
3. 安装 `fetch` 模板，让 Agent 抓取网页内容并总结
4. 禁用某个 MCP Skill，确认连接断开、工具不再可用
