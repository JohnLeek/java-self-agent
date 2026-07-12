-- V5: Agent 执行追踪表
CREATE TABLE agent_traces (
    id          BIGSERIAL PRIMARY KEY,
    session_id  VARCHAR(36) NOT NULL,
    round       INTEGER NOT NULL DEFAULT 1,
    step_order  INTEGER NOT NULL DEFAULT 0,
    step_type   VARCHAR(20) NOT NULL,
    agent_name  VARCHAR(50),
    tool_name   VARCHAR(50),
    input       TEXT,
    output      TEXT,
    metadata    JSONB DEFAULT '{}',
    status      VARCHAR(15) NOT NULL DEFAULT 'RUNNING',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_traces_session ON agent_traces(session_id, round, step_order);
CREATE INDEX idx_traces_status ON agent_traces(status);
