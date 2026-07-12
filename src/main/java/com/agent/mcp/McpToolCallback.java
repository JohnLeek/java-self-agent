package com.agent.mcp;

import com.agent.trace.TracingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.client.McpSyncClient;
import org.springframework.ai.model.function.FunctionCallback;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * MCP 工具 → Spring AI FunctionCallback 适配器。
 *
 * 将 MCP Server 发现的工具包装为标准的 FunctionCallback，
 * 与 @Tool 注解的内部 Skill 统一注册到 ChatClient。
 * LLM 分不清工具来自内部还是外部 MCP Server。
 */
public class McpToolCallback implements FunctionCallback {

    private final String name;
    private final McpSchema.Tool tool;
    private final McpSyncClient client;
    private final ObjectMapper json = new ObjectMapper();

    public McpToolCallback(String name, McpSchema.Tool tool, McpSyncClient client) {
        this.name = name;
        this.tool = tool;
        this.client = client;
    }

    @Override
    public String getName() { return name; }

    @Override
    public String getDescription() { return tool.description(); }

    @Override
    public String getInputTypeSchema() {
        try { return json.writeValueAsString(tool.inputSchema()); }
        catch (Exception e) { return "{}"; }
    }

    @Override
    @SuppressWarnings("unchecked")
    public String call(String functionArguments) {
        TracingService.recordToolCall(name);
        System.out.println("  [MCP-Tool] " + name + " ← " + functionArguments);
        try {
            Map<String, Object> args = functionArguments != null && !functionArguments.isEmpty()
                    ? json.readValue(functionArguments, Map.class) : Map.of();

            McpSchema.CallToolResult result = client.callTool(
                    new McpSchema.CallToolRequest(tool.name(), args));

            return result.content().stream()
                    .filter(c -> c instanceof McpSchema.TextContent)
                    .map(c -> ((McpSchema.TextContent) c).text())
                    .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return "MCP 工具错误 [" + name + "]: " + e.getMessage();
        }
    }

    @Override
    public String call(String fa, org.springframework.ai.chat.model.ToolContext tc) {
        return call(fa);
    }
}
