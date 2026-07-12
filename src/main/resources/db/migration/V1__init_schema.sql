-- ============================================================
-- Day6 Agent — 初始化数据库 schema
-- 策略：无外键约束，关联字段仅建普通索引，一致性由应用层保证
-- ============================================================

CREATE EXTENSION IF NOT EXISTS vector;

-- 3.0 users
CREATE TABLE users (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(100) NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 3.1 sessions
CREATE TABLE sessions (
    id          VARCHAR(36) PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    title       VARCHAR(200) NOT NULL DEFAULT '新会话',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_sessions_user_id ON sessions(user_id, updated_at DESC);

-- 3.2 messages
CREATE TABLE messages (
    id          BIGSERIAL PRIMARY KEY,
    session_id  VARCHAR(36) NOT NULL,
    role        VARCHAR(10) NOT NULL CHECK (role IN ('user', 'agent', 'tool')),
    content     TEXT NOT NULL DEFAULT '',
    tool_name   VARCHAR(50),
    token_count INTEGER,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_messages_session_id ON messages(session_id, created_at);

-- 3.3 user_profiles
CREATE TABLE user_profiles (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT NOT NULL,
    content           TEXT NOT NULL,
    source_message_id BIGINT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_user_profiles_user ON user_profiles(user_id, created_at DESC);

-- 3.4 notes
CREATE TABLE notes (
    id          BIGSERIAL PRIMARY KEY,
    session_id  VARCHAR(36) NOT NULL,
    user_id     BIGINT NOT NULL,
    content     TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_notes_session ON notes(session_id);
CREATE INDEX idx_notes_user ON notes(user_id);

-- 3.5 documents
CREATE TABLE documents (
    id          BIGSERIAL PRIMARY KEY,
    source      VARCHAR(500) NOT NULL,
    chunk_index INTEGER NOT NULL,
    content     TEXT NOT NULL,
    embedding   vector(1536),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_documents_source ON documents(source);

-- 默认用户
INSERT INTO users (id, username) VALUES (1, 'default');
