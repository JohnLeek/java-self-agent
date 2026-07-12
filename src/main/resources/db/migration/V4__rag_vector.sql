-- V4: RAG 向量检索升级
-- 1. 列维度从 vector(1536) 改为 vector(1024)，适配 BGE-large-zh-v1.5
ALTER TABLE documents ALTER COLUMN embedding TYPE vector(1024);

-- 2. HNSW 索引：快的查询 + 慢的构建，适合读多写少
CREATE INDEX IF NOT EXISTS idx_documents_embedding ON documents
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 200);
