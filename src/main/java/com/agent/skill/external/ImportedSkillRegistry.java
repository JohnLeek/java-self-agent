package com.agent.skill.external;

import com.agent.dao.ImportedSkill;
import com.agent.mcp.McpClientManager;
import com.agent.repository.ImportedSkillRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 外部 Skill 注册中心。
 *
 * 管理所有用户导入的 Skill 的生命周期：
 *   - TOOL Skill（HTTP/SCRIPT）→ ExternalSkillCallback
 *   - MCP Skill → McpToolCallback（自动发现工具）
 *   - 统一提供给 ChatService 注册到 ChatClient
 */
@Component
public class ImportedSkillRegistry {

    private final ImportedSkillRepository repo;
    private final RestClient.Builder restClientBuilder;
    private final ScriptExecutor scriptExecutor;
    private final McpClientManager mcpClientManager;
    private final ObjectMapper json = new ObjectMapper();

    public ImportedSkillRegistry(ImportedSkillRepository repo,
                                  RestClient.Builder restClientBuilder,
                                  ScriptExecutor scriptExecutor,
                                  McpClientManager mcpClientManager) {
        this.repo = repo;
        this.restClientBuilder = restClientBuilder;
        this.scriptExecutor = scriptExecutor;
        this.mcpClientManager = mcpClientManager;
    }

    /**
     * 获取所有已启用 Skill 的 FunctionCallback 列表。
     * 包括 TOOL（HTTP/SCRIPT）和 MCP 类型。
     * 供 ChatService 在构建 ChatClient 时注册。
     */
    public List<FunctionCallback> toToolCallbacks(Long userId) {
        List<FunctionCallback> callbacks = new ArrayList<>();

        // TOOL 类型：HTTP / SCRIPT
        List<ImportedSkill> toolSkills = repo.findByUserIdAndTypeAndEnabledOrderByPriorityDesc(userId, "TOOL", true);
        for (ImportedSkill skill : toolSkills) {
            try {
                Map<String, Object> def = json.readValue(skill.getSkillJson(), Map.class);
                List<Map<String, Object>> tools = (List<Map<String, Object>>) def.get("tools");
                if (tools == null) continue;

                for (Map<String, Object> tool : tools) {
                    String name = (String) tool.get("name");
                    String desc = (String) tool.get("description");
                    Map<String, Object> params = (Map<String, Object>) tool.get("parameters");
                    Map<String, Object> executor = (Map<String, Object>) tool.get("executor");

                    String schema = json.writeValueAsString(params);
                    callbacks.add(new ExternalSkillCallback(
                            name, desc, schema, executor, restClientBuilder, scriptExecutor));
                }
            } catch (Exception e) {
                System.err.println("  [ImportedSkillRegistry] 解析 TOOL Skill 失败: " + skill.getName() + " - " + e.getMessage());
            }
        }

        // MCP 类型：启动 MCP Server 并自动发现工具
        List<ImportedSkill> mcpSkills = repo.findByUserIdAndTypeAndEnabledOrderByPriorityDesc(userId, "MCP", true);
        System.out.println("[ImportedSkillRegistry] 发现 " + mcpSkills.size() + " 个启用的 MCP Skill");
        for (ImportedSkill skill : mcpSkills) {
            System.out.println("[ImportedSkillRegistry]   DB记录: id=" + skill.getId()
                    + " name=" + skill.getName() + " enabled=" + skill.getEnabled()
                    + " json=" + (skill.getSkillJson() != null ? skill.getSkillJson().substring(0, Math.min(80, skill.getSkillJson().length())) : "null"));
            try {
                Map<String, Object> def = json.readValue(skill.getSkillJson(), Map.class);
                Map<String, Object> server = (Map<String, Object>) def.get("server");
                if (server == null) {
                    System.err.println("  [ImportedSkillRegistry] MCP Skill 缺少 server 配置: " + skill.getName());
                    continue;
                }
                String command = (String) server.get("command");
                List<String> args = (List<String>) server.get("args");
                Map<String, String> env = (Map<String, String>) server.get("env");
                System.out.println("[ImportedSkillRegistry] 连接 MCP: " + command + " " + String.join(" ", args));

                List<FunctionCallback> mcpCallbacks = mcpClientManager.connectAndDiscover(
                        skill.getId(), command, args, env);
                System.out.println("[ImportedSkillRegistry] MCP Skill [" + skill.getName() + "] 注册了 " + mcpCallbacks.size() + " 个工具: "
                        + mcpCallbacks.stream().map(c -> c.getName()).toList());
                callbacks.addAll(mcpCallbacks);
            } catch (Exception e) {
                System.err.println("  [ImportedSkillRegistry] MCP Skill 连接失败: " + skill.getName() + " - " + e.getMessage());
            }
        }

        System.out.println("[ImportedSkillRegistry] 总计 " + callbacks.size() + " 个外部工具回调");
        return callbacks;
    }
}
