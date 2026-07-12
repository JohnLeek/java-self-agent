package com.agent.controller;

import com.agent.dao.ImportedSkill;
import com.agent.mcp.McpClientManager;
import com.agent.repository.ImportedSkillRepository;
import com.agent.skill.external.McpTemplate;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 外部 Skill 管理接口。
 */
@RestController
@RequestMapping("/api/skills")
public class SkillController {

    private static final Long DEFAULT_USER_ID = 1L;

    @Resource private ImportedSkillRepository skillRepo;
    @Resource private McpClientManager mcpClientManager;

    /** 列出所有已导入的 Skill */
    @GetMapping
    public java.util.List<ImportedSkill> list() {
        return skillRepo.findByUserIdOrderByPriorityDesc(DEFAULT_USER_ID);
    }

    /** 导入新 Skill */
    @PostMapping
    @SuppressWarnings("unchecked")
    public ImportedSkill importSkill(@RequestBody Map<String, Object> body) {
        ImportedSkill skill = new ImportedSkill();
        skill.setUserId(DEFAULT_USER_ID);
        skill.setEnabled(true);
        skill.setName((String) body.getOrDefault("name", ""));
        skill.setType((String) body.getOrDefault("type", "PROMPT"));
        skill.setDisplayName((String) body.get("displayName"));
        skill.setDescription((String) body.getOrDefault("description", ""));
        skill.setPriority(body.containsKey("priority") ? ((Number) body.get("priority")).intValue() : 0);
        skill.setContent((String) body.get("content"));
        skill.setSkillJson((String) body.get("skillJson"));

        Object triggers = body.get("triggers");
        if (triggers instanceof List) skill.setTriggers((List<String>) triggers);
        Object kw = body.get("keywords");
        if (kw instanceof List) skill.setKeywords((List<String>) kw);

        return skillRepo.save(skill);
    }

    /** 启用 */
    @PutMapping("/{id}/enable")
    public Map<String, String> enable(@PathVariable Long id) {
        skillRepo.findById(id).ifPresent(s -> { s.setEnabled(true); skillRepo.save(s); });
        return Map.of("status", "enabled");
    }

    /** 禁用（MCP 类型会断开连接） */
    @PutMapping("/{id}/disable")
    public Map<String, String> disable(@PathVariable Long id) {
        skillRepo.findById(id).ifPresent(s -> {
            s.setEnabled(false); skillRepo.save(s);
            if ("MCP".equals(s.getType())) mcpClientManager.disconnect(id);
        });
        return Map.of("status", "disabled");
    }

    /** 卸载（MCP 类型会断开连接） */
    @DeleteMapping("/{id}")
    public Map<String, String> delete(@PathVariable Long id) {
        skillRepo.findById(id).ifPresent(s -> {
            if ("MCP".equals(s.getType())) mcpClientManager.disconnect(id);
        });
        skillRepo.deleteById(id);
        return Map.of("status", "deleted");
    }

    /** 刷新 MCP 连接（重新发现工具） */
    @PostMapping("/refresh")
    public Map<String, String> refresh() {
        mcpClientManager.refresh();
        return Map.of("status", "refreshed");
    }

    /** MCP Server 模板库 */
    @GetMapping("/mcp-templates")
    public List<McpTemplate> mcpTemplates() {
        return McpTemplate.BUILTIN;
    }
}
