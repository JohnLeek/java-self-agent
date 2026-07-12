# Day 4：Agentic RAG — 检索增强生成

## 学习目标

从"切片 → 向量化 → 检索 → 回答"完整链路，理解 RAG 的核心组件和自纠正检索。

## RAG 架构总览

```
文档上传 → 切片（500字符/50重叠）
         → BGE-large-zh-v1.5 嵌入（1024维向量）
         → pgvector HNSW 索引

用户提问 → 查询嵌入 → 余弦相似度检索 → Top-K 结果 + 相似度评分
         → Agent 基于检索结果回答（带来源引用）
```

## 向量服务：RagVectorService

`src/main/java/com/agent/rag/vector/RagVectorService.java`：

### 文档摄入

```java
public int ingest(Path path) {
    String source = path.getFileName().toString();
    String content = Files.readString(path);
    List<String> chunks = split(content);  // 切片

    // 删除旧数据，支持重新摄入
    jdbc.update("DELETE FROM documents WHERE source = ?", source);

    // 批量 Embedding
    List<float[]> embeddings = embedding.embedBatch(chunks);

    // 批量入库 pgvector
    for (int i = 0; i < chunks.size(); i++) {
        jdbc.update(
            "INSERT INTO documents (source, chunk_index, content, embedding) " +
            "VALUES (?, ?, ?, ?::vector)",
            source, i, chunks.get(i), vectorToString(embeddings.get(i)));
    }
    return chunks.size();
}
```

### 切片策略

```java
private List<String> split(String text) {
    int chunkSize = 500, overlap = 50;
    // 优先在段落边界(\n\n)处切分
    // 找不到段落边界则按行(\n)切分
    // 相邻块之间有 50 字符重叠
}
```

### 语义搜索

```java
public SearchResult search(String query, int topK, double threshold) {
    // 1. 查询向量化
    float[] queryVec = embedding.embed(query);

    // 2. pgvector 余弦相似度搜索
    String sql = """
        SELECT source, chunk_index, content,
               ROUND((1 - (embedding <=> ?::vector))::numeric, 4) AS similarity
        FROM documents
        WHERE 1 - (embedding <=> ?::vector) > ?
        ORDER BY embedding <=> ?::vector
        LIMIT ?
        """;

    // 3. 返回排序结果 + 相似度评分
    // similarity 范围 0~1，越大越相似
}
```

> 核心 SQL：`1 - (embedding <=> query_vector)` 计算余弦相似度。`<=>` 是 pgvector 的余弦距离运算符。

## EmbeddingClient

`src/main/java/com/agent/rag/vector/EmbeddingClient.java`：

- 调用本地 BGE 嵌入服务（Python Flask，端口 9999）
- 使用 OpenAI 兼容的请求/响应格式
- 降级策略：BGE 服务不可用时，回退伪嵌入（确定性哈希），保证开发链路通畅
- 向量维度：1024

## RagTool — Agent 可调用的检索工具

`src/main/java/com/agent/rag/RagTool.java`：

```java
@Component
public class RagTool {
    @Tool(description = "搜索本地知识库，返回语义相似的文档片段及相似度评分")
    public String search(@ToolParam(description = "搜索关键词或问题") String query) {
        TracingService.recordToolCall("search");
        SearchResult result = ragVectorService.search(query, 5, 0.6);
        return result.text();
    }
}
```

## System Prompt 中的 RAG 自纠正规则

Single Agent 模式下，`ChatService.buildSingleAgentPrompt()` 会自动检索知识库并注入上下文：

```java
// 自动检索知识库
if (ragVectorService.count() > 0) {
    var r = ragVectorService.search(userMessage, 3, 0.5);
    if (!r.chunks().isEmpty()) {
        ragCtx.append("【知识库自动检索】\n").append(r.text());
    }
}
```

Agent 还应该根据相似度评分判断检索质量：

```
检索到结果后，检查相似度分数：
- 分数 > 0.8：结果可靠，基于此回答
- 分数 0.5-0.8：结果部分相关，考虑用不同关键词重新搜索
- 分数 < 0.5：结果不可靠，告知用户知识库未覆盖此问题
```

## 数据库

`src/main/resources/db/migration/V4__rag_vector.sql`：

```sql
-- 向量列从 1536 改为 1024（适配 BGE-large-zh-v1.5）
ALTER TABLE documents ALTER COLUMN embedding TYPE vector(1024);

-- HNSW 近似最近邻索引
CREATE INDEX IF NOT EXISTS documents_embedding_idx
    ON documents USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 200);
```

HNSW 参数说明：
- `m=16`：每层每个节点的最大连接数
- `ef_construction=200`：构建时搜索宽度，越大精确度越高但构建越慢

## 降级策略

`embedding_server.py` 服务不可用时的处理：

```java
// 伪嵌入：确定性哈希 → 1024 维向量
// 虽不如真实嵌入准确，但保证开发链路不中断
private float[] pseudoEmbed(String text) { ... }
```

## 关键文件

| 文件 | 作用 |
|------|------|
| `rag/vector/RagVectorService.java` | pgvector 向量检索服务 |
| `rag/vector/EmbeddingClient.java` | BGE 嵌入客户端 |
| `rag/RagTool.java` | Agent 可调用的检索工具 |
| `rag/DocumentStore.java` | 内存关键词搜索（旧版） |
| `rag/IngestionService.java` | 文档摄取服务（旧版） |
| `embedding_server.py` | Python BGE 嵌入服务 |
| `db/migration/V4__rag_vector.sql` | 向量维度 + HNSW 索引 |

## 验收标准

- [x] 上传文档后切分为合适的块
- [x] 向量化入库，pgvector 能检索到相关内容
- [x] Agent 基于检索结果回答，带相似度评分
- [x] BGE 服务不可用时自动降级（伪嵌入）
- [x] 支持按 source 删除文档、重新摄入

## 动手实验

```bash
# 1. 启动嵌入服务
pip install flask torch transformers
python embedding_server.py

# 2. 上传文档
curl -X POST "http://localhost:8080/api/rag/upload" \
  -F "file=@test-doc.md"

# 3. 提问
curl "http://localhost:8080/api/chat/stream?message=文档中提到的架构是什么&sessionId=xxx"

# 4. 查看索引状态
curl "http://localhost:8080/api/rag/status"
```
