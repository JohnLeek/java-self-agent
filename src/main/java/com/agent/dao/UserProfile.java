package com.agent.dao;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_profiles")
public class UserProfile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "source_message_id")
    private Long sourceMessageId;

    @Column(updatable = false)
    private Instant createdAt = Instant.now();

    public UserProfile() {}

    public static UserProfile create(Long userId, String content, Long sourceMessageId) {
        UserProfile p = new UserProfile();
        p.userId = userId; p.content = content; p.sourceMessageId = sourceMessageId;
        return p;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getContent() { return content; }
    public Long getSourceMessageId() { return sourceMessageId; }
    public Instant getCreatedAt() { return createdAt; }
}
