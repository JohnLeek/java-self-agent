package com.agent.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.function.FunctionCallback;

import java.util.List;

/**
 * 专家 Agent — 拥有有限工具集和专属 system prompt。
 * 每个 Agent 内部是独立的 ChatClient，被 Orchestrator 通过 @Tool 委托调用。
 */
public class SpecialistAgent {

    private final String name;
    private final ChatClient client;

    public SpecialistAgent(String name, ChatClient.Builder builder,
                           String systemPrompt, Object... tools) {
        this.name = name;
        this.client = builder.defaultTools(tools).build();
    }

    /** 支持额外 FunctionCallback（如 MCP 工具） */
    public SpecialistAgent(String name, ChatClient.Builder builder,
                           String systemPrompt,
                           List<FunctionCallback> extraCallbacks,
                           Object... tools) {
        this.name = name;
        ChatClient.Builder b = builder.defaultTools(tools);
        if (extraCallbacks != null && !extraCallbacks.isEmpty()) {
            b = b.defaultFunctions(extraCallbacks.toArray(new FunctionCallback[0]));
        }
        this.client = b.build();
    }

    /** 被 Orchestrator 委托执行任务 */
    public String delegate(String task) {
        System.out.println("  [" + name + "] 收到: " + task);
        String result = client.prompt().user(task).call().content();
        System.out.println("  [" + name + "] 完成");
        return result;
    }

    public String getName() { return name; }
}
