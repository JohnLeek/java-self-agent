package com.agent.skill.external;

import com.agent.dao.ImportedSkill;
import com.agent.repository.ImportedSkillRepository;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Prompt Skill 路由层。
 *
 * 根据用户消息匹配已启用的 PROMPT 类型 Skill，
 * 返回匹配到的 Skill 文档，拼入 system prompt。
 *
 * 匹配策略：
 *   1. triggers 为空 → 全局 Skill，始终激活
 *   2. 用户消息包含任意 trigger 关键词 → 激活
 *   3. 同时激活最多 3 个，按优先级排序
 */
@Component
public class SkillRouter {

    private final ImportedSkillRepository repo;
    private static final int MAX_ACTIVE = 3;

    public SkillRouter(ImportedSkillRepository repo) {
        this.repo = repo;
    }

    /**
     * 根据用户消息路由匹配的 Prompt Skill，返回拼接好的 prompt 文本。
     */
    public String routePromptSkills(Long userId, String userMessage) {
        List<ImportedSkill> skills = repo.findByUserIdAndTypeAndEnabledOrderByPriorityDesc(userId, "PROMPT", true);

        List<ImportedSkill> matched = skills.stream()
                .filter(s -> matches(s, userMessage))
                .sorted(Comparator.comparing(ImportedSkill::getPriority).reversed())
                .limit(MAX_ACTIVE)
                .toList();

        if (matched.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (ImportedSkill s : matched) {
            sb.append("【已加载 Skill: ").append(s.getDisplayName()).append("】\n");
            sb.append(s.getContent()).append("\n\n");
            System.out.println("  [SkillRouter] 激活 Prompt Skill: " + s.getName());
        }
        return sb.toString();
    }

    private boolean matches(ImportedSkill skill, String userMessage) {
        // triggers 为空 → 全局 Skill，始终激活
        if (skill.getTriggers() == null || skill.getTriggers().isEmpty()) {
            return true;
        }
        // 关键词匹配
        return skill.getTriggers().stream()
                .anyMatch(t -> userMessage.toLowerCase().contains(t.toLowerCase()));
    }
}
