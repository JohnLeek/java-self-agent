package com.agent.config;

import com.agent.agent.SpecialistAgent;
import com.agent.memory.MemoryTools;
import com.agent.rag.RagTool;
import com.agent.skill.external.ImportedSkillRegistry;
import com.agent.skill.impl.CalculatorSkill;
import com.agent.skill.impl.DiceSkill;
import com.agent.skill.impl.TranslationSkill;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 多 Agent 模式配置 — 创建三个 SpecialistAgent。
 * 工具专家同时获得内部工具和外部 MCP 工具。
 */
@Configuration
public class AgentModeConfig {

    private static final Long DEFAULT_USER_ID = 1L;

    @Bean
    SpecialistAgent searchAgent(ObjectProvider<ChatClient.Builder> provider, RagTool ragTool,
                                 ImportedSkillRegistry importedRegistry) {
        return new SpecialistAgent("搜索专家", provider.getObject(),
                "你是搜索专家。用 search 工具在知识库中检索，基于结果回答。也可以用可用的文件/网络工具获取信息。",
                importedRegistry.toToolCallbacks(DEFAULT_USER_ID),
                ragTool);
    }

    @Bean
    SpecialistAgent toolAgent(ObjectProvider<ChatClient.Builder> provider,
                              CalculatorSkill calculator, DiceSkill dice,
                              TranslationSkill translation,
                              ImportedSkillRegistry importedRegistry) {
        return new SpecialistAgent("工具专家", provider.getObject(),
                "你是工具专家。用所有可用的工具完成任务，包括内部工具和外部 MCP 工具（如文件系统操作、网络请求等）。",
                importedRegistry.toToolCallbacks(DEFAULT_USER_ID),
                calculator, dice, translation);
    }

    @Bean
    SpecialistAgent memoryAgent(ObjectProvider<ChatClient.Builder> provider, MemoryTools memoryTools) {
        return new SpecialistAgent("记忆专家", provider.getObject(),
                "你是记忆专家。用 noteToSelf/readMyNotes/getUserProfile 管理用户信息和笔记。",
                memoryTools);
    }
}
