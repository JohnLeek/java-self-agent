package com.agent.skill;

/** 可插拔 Skill 接口 */
public interface Skill {
    String name();
    String description();
    boolean isEnabled();
    void setEnabled(boolean enabled);
}
