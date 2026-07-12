package com.agent.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PreDestroy;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * MCP 客户端管理器 —— 管理外部 MCP Server 的连接生命周期。
 *
 * 每个 MCP Skill 对应一个 MCP Server 进程（通过 stdio 通信），
 * 连接后自动发现该 Server 提供的所有工具，包装为 FunctionCallback。
 *
 * 连接会被缓存。失败也缓存（不重试），防止启动卡住。
 */
@Component
public class McpClientManager {

    private static final McpJsonMapper JSON_MAPPER = new JacksonMcpJsonMapper(new ObjectMapper());
    private static final long TIMEOUT_SECONDS = 10;

    private final ConcurrentHashMap<Long, McpSyncClient> clients = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, List<FunctionCallback>> callbacksCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Boolean> failedCache = new ConcurrentHashMap<>();

    /**
     * 连接 MCP Server 并发现工具（带超时保护，不会阻塞启动）。
     */
    public List<FunctionCallback> connectAndDiscover(Long skillId, String command,
                                                      List<String> args, Map<String, String> env) {
        List<FunctionCallback> cached = callbacksCache.get(skillId);
        if (cached != null) return cached;

        // 已知失败的不重试（AgentModeConfig 两个 bean 都会调用）
        if (failedCache.containsKey(skillId)) return List.of();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<List<FunctionCallback>> future = executor.submit(() -> {
            ServerParameters.Builder paramsBuilder = ServerParameters.builder(command).args(args);
            if (env != null && !env.isEmpty()) paramsBuilder.env(env);

            StdioClientTransport transport = new StdioClientTransport(paramsBuilder.build(), JSON_MAPPER);
            McpSyncClient client = McpClient.sync(transport).build();
            client.initialize();

            List<McpSchema.Tool> tools = client.listTools().tools();
            System.out.println("[MCP] " + command + " 发现 " + tools.size() + " 个工具: "
                    + tools.stream().map(McpSchema.Tool::name).toList());

            List<FunctionCallback> callbacks = new ArrayList<>();
            for (McpSchema.Tool tool : tools) {
                callbacks.add(new McpToolCallback("mcp_" + tool.name(), tool, client));
            }

            clients.put(skillId, client);
            return callbacks;
        });

        try {
            List<FunctionCallback> result = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            callbacksCache.put(skillId, result);
            return result;
        } catch (TimeoutException e) {
            System.err.println("[MCP] 连接超时 (" + TIMEOUT_SECONDS + "s) skillId=" + skillId + " command=" + command);
            future.cancel(true);
            failedCache.put(skillId, true);
            return List.of();
        } catch (Exception e) {
            System.err.println("[MCP] 连接失败 skillId=" + skillId + " command=" + command + ": " + e.getMessage());
            failedCache.put(skillId, true);
            return List.of();
        } finally {
            executor.shutdownNow();
        }
    }

    /** 断开指定 Skill 的 MCP 连接并清除缓存 */
    public void disconnect(Long skillId) {
        McpSyncClient client = clients.remove(skillId);
        if (client != null) {
            try { client.close(); } catch (Exception ignored) {}
        }
        callbacksCache.remove(skillId);
        failedCache.remove(skillId);
    }

    /** 刷新所有连接（重新发现工具） */
    public void refresh() {
        clients.values().forEach(c -> { try { c.close(); } catch (Exception ignored) {} });
        clients.clear();
        callbacksCache.clear();
        failedCache.clear();
    }

    @PreDestroy
    public void close() {
        clients.values().forEach(c -> { try { c.close(); } catch (Exception ignored) {} });
        clients.clear();
        callbacksCache.clear();
        failedCache.clear();
    }
}
