package com.agent.dao;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "messages")
public class Message {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, length = 36)
    private String sessionId;

    @Column(nullable = false, length = 10)
    private String role;  // user / agent / tool

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content = "";

    @Column(name = "tool_name", length = 50)
    private String toolName;

    @Column(name = "token_count")
    private Integer tokenCount;

    @Column(updatable = false)
    private Instant createdAt = Instant.now();

    public Message() {}

    public static Message user(String sessionId, String content) {
        Message m = new Message();
        m.sessionId = sessionId; m.role = "user"; m.content = content;
        return m;
    }

    public static Message agent(String sessionId, String content) {
        Message m = new Message();
        m.sessionId = sessionId; m.role = "agent"; m.content = content;
        return m;
    }

    public static Message tool(String sessionId, String toolName, String content) {
        Message m = new Message();
        m.sessionId = sessionId; m.role = "tool"; m.toolName = toolName; m.content = content;
        return m;
    }

    public Long getId() { return id; }
    public String getSessionId() { return sessionId; }
    public String getRole() { return role; }
    public String getContent() { return content; }
    public String getToolName() { return toolName; }
    public Integer getTokenCount() { return tokenCount; }
    public Instant getCreatedAt() { return createdAt; }
}
