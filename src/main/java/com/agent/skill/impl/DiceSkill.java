package com.agent.skill.impl;

import com.agent.skill.Skill;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import java.util.concurrent.ThreadLocalRandom;

/** 掷骰子 Skill — LLM 无法预测，必然触发工具调用 */
@Component
public class DiceSkill implements Skill {
    private boolean enabled = true;

    @Override public String name() { return "dice"; }
    @Override public String description() { return "掷骰子：生成随机数"; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean e) { enabled = e; }

    @Tool(description = "掷骰子产生随机数，LLM不能自己生成")
    public String roll(@ToolParam(description = "骰子面数") int sides) {
        int r = ThreadLocalRandom.current().nextInt(sides) + 1;
        return "掷 d" + sides + " = **" + r + "**";
    }
}
