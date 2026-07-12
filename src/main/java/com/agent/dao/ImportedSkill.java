package com.agent.dao;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "imported_skills")
public class ImportedSkill {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 10)
    private String type = "PROMPT";  // TOOL / PROMPT

    @Column(name = "display_name", length = 200)
    private String displayName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "triggers", columnDefinition = "TEXT")
    private String triggersStr;

    @Column(name = "keywords", columnDefinition = "TEXT")
    private String keywordsStr;

    private Integer priority = 0;

    @Column(columnDefinition = "TEXT")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "skill_json", columnDefinition = "JSONB")
    private String skillJson;

    private Boolean enabled = true;

    @Column(updatable = false)
    private Instant createdAt = Instant.now();

    private Instant updatedAt = Instant.now();

    public ImportedSkill() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<String> getTriggers() {
        return triggersStr != null && !triggersStr.isEmpty()
                ? List.of(triggersStr.split(",")) : List.of();
    }
    public void setTriggers(List<String> triggers) {
        this.triggersStr = triggers != null ? String.join(",", triggers) : null;
    }
    public List<String> getKeywords() {
        return keywordsStr != null && !keywordsStr.isEmpty()
                ? List.of(keywordsStr.split(",")) : List.of();
    }
    public void setKeywords(List<String> keywords) {
        this.keywordsStr = keywords != null ? String.join(",", keywords) : null;
    }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getSkillJson() { return skillJson; }
    public void setSkillJson(String skillJson) { this.skillJson = skillJson; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
