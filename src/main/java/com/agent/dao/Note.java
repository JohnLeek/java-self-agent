package com.agent.dao;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "notes")
public class Note {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, length = 36)
    private String sessionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(updatable = false)
    private Instant createdAt = Instant.now();

    public Note() {}

    public static Note create(String sessionId, Long userId, String content) {
        Note n = new Note();
        n.sessionId = sessionId; n.userId = userId; n.content = content;
        return n;
    }

    public Long getId() { return id; }
    public String getSessionId() { return sessionId; }
    public Long getUserId() { return userId; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
}
