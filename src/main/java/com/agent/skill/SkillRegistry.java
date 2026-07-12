package com.agent.skill;

import org.springframework.stereotype.Component;
import java.util.*;
import java.util.stream.Collectors;

/** Skill 注册中心：发现、启用、禁用 */
@Component
public class SkillRegistry {
    private final Map<String, Skill> skills = new LinkedHashMap<>();

    public SkillRegistry(List<Skill> all) { all.forEach(s -> skills.put(s.name(), s)); }

    public Collection<Skill> all() { return skills.values(); }

    public List<Object> enabledBeans() {
        return skills.values().stream().filter(Skill::isEnabled).map(s -> (Object) s).collect(Collectors.toList());
    }

    public void enable(String n) { Skill s = skills.get(n); if (s != null) s.setEnabled(true); }
    public void disable(String n) { Skill s = skills.get(n); if (s != null) s.setEnabled(false); }
}
