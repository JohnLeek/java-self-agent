# Day 2：Tool / Skill 可插拔系统

## 学习目标

用 Spring AI 的 `@Tool` 注解规范化工具，设计可插拔 Skill 架构，理解工具描述对 LLM 调用准确率的影响。

## Skill 接口设计

`src/main/java/com/agent/skill/Skill.java`：

```java
/** 可插拔 Skill 接口 */
public interface Skill {
    String name();
    String description();
    boolean isEnabled();
    void setEnabled(boolean enabled);
}
```

极简接口 —— 技能只需要提供名称、描述和启停开关。具体执行逻辑通过 Spring AI 的 `@Tool` 注解暴露。

## 内置技能

### CalculatorSkill — 安全表达式计算器

`src/main/java/com/agent/skill/impl/CalculatorSkill.java`

- 递归下降解析器，支持 `+ - * / ** sqrt abs sin cos log exp PI`
- 不使用 `eval`，无代码注入风险
- `@Tool(description = "计算数学表达式...")` 直接暴露给 LLM

### TranslationSkill — LLM 翻译

`src/main/java/com/agent/skill/impl/TranslationSkill.java`

- 内部调用 LLM 进行中英/英中翻译
- `@Tool(description = "翻译文本...")` 和参数描述直接影响 LLM 何时选择此工具

### DiceSkill — 随机数生成

`src/main/java/com/agent/skill/impl/DiceSkill.java`

- `@Tool(description = "掷骰子...")` 生成真随机数

**关键理解**：`@Tool` 注解的 `description` 直接生成 JSON Schema 给 LLM，措辞准确度决定工具调用准确率。

## SkillRegistry 注册中心

`src/main/java/com/agent/skill/SkillRegistry.java`：

```java
@Component
public class SkillRegistry {
    private final List<Skill> skills;

    // Spring 自动注入所有 Skill 实现
    public SkillRegistry(List<Skill> skills) { this.skills = skills; }

    // 把启用状态的 skill 作为 Object 暴露给 ChatClient
    public List<Object> enabledBeans() {
        return skills.stream()
            .filter(Skill::isEnabled)
            .collect(Collectors.toList());
    }

    public void enable(String name) { ... }
    public void disable(String name) { ... }
}
```

## 外部技能系统

用户可以在前端导入三种类型的外部技能，运行时热插拔，无需重启：

### PROMPT 类型

Markdown 提示词注入，通过触发器关键词匹配激活。

`src/main/java/com/agent/skill/external/SkillRouter.java`：

- 加载所有已启用的 PROMPT 技能
- 触发器为空 = 全局激活
- 触发器匹配 = 子字符串匹配用户消息
- 最多激活 3 个，按优先级排序

### TOOL 类型（HTTP / Script）

`src/main/java/com/agent/skill/external/ExternalSkillCallback.java`：

- **HTTP 执行器**：`RestClient` 发送 HTTP 请求，`{param}` 占位符自动替换
- **Script 执行器**：委托给 `ScriptExecutor`

`src/main/java/com/agent/skill/external/ScriptExecutor.java`：

```java
// 安全措施：
// - 运行时白名单：python3 / bash / node
// - 脚本最大 10,000 字符
// - 临时文件隔离
// - 30 秒超时保护
```

### MCP 类型

见 Day 3 详细介绍。

### 导入一个 Skill 的 JSON 格式

```json
{
  "tools": [
    {
      "name": "get_weather",
      "description": "查询指定城市的天气",
      "parameters": {
        "type": "object",
        "properties": {
          "city": {"type": "string", "description": "城市名称"}
        },
        "required": ["city"]
      },
      "executor": {
        "type": "http",
        "method": "GET",
        "url": "https://api.weather.com/v1/{city}"
      }
    }
  ]
}
```

## 工具注册流程

`ChatService.java:71-86`：

```java
private ChatClient buildChatClient() {
    var builder = builderProvider.getObject()
            .defaultTools(skills.enabledBeans().toArray())   // 内置技能
            .defaultTools(memoryTools)                        // 记忆工具
            .defaultTools(ragTool);                           // RAG 工具

    // 外部技能（TOOL + MCP）
    var externalCallbacks = importedRegistry.toToolCallbacks(DEFAULT_USER_ID);
    if (!externalCallbacks.isEmpty()) {
        builder = builder.defaultFunctions(
            externalCallbacks.toArray(new FunctionCallback[0]));
    }
    return builder.build();
}
```

## System Prompt 中的工具使用规则

`ChatService.java:128-141`：

```
工具使用规则（必须遵守）：
1. 用户要求"记一下/帮我记/提醒/记录"→必须调noteToSelf，不要用文字代替
2. 用户要求"查看笔记/历史记录/之前记了什么"→必须调readMyNotes
3. 用户问数学计算→必须调calculator，不要心算
4. 用户问知识库问题→调search
5. 用户要求翻译→调translation
6. 用户要求随机数/掷骰子→调dice
7. 用户问个人信息→调getUserProfile

关键：执行操作后只回复工具返回的结果，不要自己编造内容。
```

## 关键文件

| 文件 | 作用 |
|------|------|
| `skill/Skill.java` | Skill 接口定义 |
| `skill/SkillRegistry.java` | 技能注册中心 |
| `skill/impl/CalculatorSkill.java` | 表达式计算器 |
| `skill/impl/TranslationSkill.java` | LLM 翻译 |
| `skill/impl/DiceSkill.java` | 随机骰子 |
| `skill/external/ImportedSkillRegistry.java` | 外部技能注册 |
| `skill/external/ExternalSkillCallback.java` | HTTP/Script 工具适配器 |
| `skill/external/ScriptExecutor.java` | 安全脚本执行沙箱 |
| `skill/external/SkillRouter.java` | PROMPT 技能路由匹配 |

## 验收标准

- [x] Skill 可注册、可卸载、可运行时启用/禁用
- [x] 同一次对话中 Agent 自动切换多个 Skill
- [x] 外部技能通过前端导入后立即可用
- [x] 工具描述措辞改变能观察到 Agent 行为变化
- [x] ScriptExecutor 安全沙箱：白名单运行时 + 超时保护

## 动手实验

1. 修改 CalculatorSkill 的 `@Tool(description=...)` 措辞，观察 Agent 何时调用它
2. 通过前端 SkillPanel 导入一个 HTTP 类型的天气查询 Skill
3. 在前端禁用翻译 Skill，看 Agent 是否不再使用它
