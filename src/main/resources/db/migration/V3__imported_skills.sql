CREATE TABLE imported_skills (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    name        VARCHAR(100) NOT NULL,
    type        VARCHAR(10) NOT NULL DEFAULT 'PROMPT',
    display_name VARCHAR(200),
    description TEXT NOT NULL,
    triggers    TEXT,
    keywords    TEXT,
    priority    INTEGER DEFAULT 0,
    content     TEXT,
    skill_json  JSONB,
    enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_imported_skills_user ON imported_skills(user_id, enabled);
CREATE INDEX idx_imported_skills_type ON imported_skills(user_id, type, enabled);
