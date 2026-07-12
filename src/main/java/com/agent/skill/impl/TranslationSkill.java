package com.agent.skill.impl;

import com.agent.skill.Skill;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/** 翻译 Skill — 内部调 LLM */
@Component
public class TranslationSkill implements Skill {
    private boolean enabled = true;
    private final ChatClient client;

    public TranslationSkill(ChatClient.Builder b) { this.client = b.build(); }

    @Override public String name() { return "translation"; }
    @Override public String description() { return "翻译：中英互译"; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean e) { enabled = e; }

    @Tool(description = "翻译文本到指定目标语言")
    public String translate(
            @ToolParam(description = "待翻译文本") String text,
            @ToolParam(description = "目标语言：中文或English") String target) {
        return client.prompt().user("翻译为" + target + "，只输出译文：\n" + text).call().content();
    }
}
