package com.agent.dao;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name = "agent_traces")
public class AgentTrace {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, length = 36)
    private String sessionId;

    @Column(nullable = false)
    private Integer round = 1;

    @Column(name = "step_order", nullable = false)
    private Integer stepOrder = 0;

    @Column(name = "step_type", nullable = false, length = 20)
    private String stepType;   // PLAN / AGENT_CALL / TOOL_CALL / SUMMARY / CHUNK

    @Column(name = "agent_name", length = 50)
    private String agentName;

    @Column(name = "tool_name", length = 50)
    private String toolName;

    @Column(columnDefinition = "TEXT")
    private String input;

    @Column(columnDefinition = "TEXT")
    private String output;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSONB")
    private String metadata = "{}";

    @Column(nullable = false, length = 15)
    private String status = "RUNNING";

    @Column(updatable = false)
    private Instant createdAt = Instant.now();

    public AgentTrace() {}

    public static AgentTrace create(String sessionId, int round, int order, String stepType,
                                     String agentName, String input, String output, String status) {
        AgentTrace t = new AgentTrace();
        t.sessionId = sessionId; t.round = round; t.stepOrder = order;
        t.stepType = stepType; t.agentName = agentName; t.input = input; t.output = output; t.status = status;
        return t;
    }

    public Long getId() { return id; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String v) { sessionId = v; }
    public Integer getRound() { return round; }
    public void setRound(Integer v) { round = v; }
    public Integer getStepOrder() { return stepOrder; }
    public void setStepOrder(Integer v) { stepOrder = v; }
    public String getStepType() { return stepType; }
    public void setStepType(String v) { stepType = v; }
    public String getAgentName() { return agentName; }
    public void setAgentName(String v) { agentName = v; }
    public String getToolName() { return toolName; }
    public void setToolName(String v) { toolName = v; }
    public String getInput() { return input; }
    public void setInput(String v) { input = v; }
    public String getOutput() { return output; }
    public void setOutput(String v) { output = v; }
    public String getMetadata() { return metadata; }
    public void setMetadata(String v) { metadata = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { status = v; }
    public Instant getCreatedAt() { return createdAt; }
}
