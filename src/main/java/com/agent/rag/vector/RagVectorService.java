package com.agent.rag.vector;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG 向量服务 — 基于 pgvector 的语义检索。
 *
 * 核心 SQL：
 *   cosine 相似度: 1 - (embedding <=> query_vector)
 *   范围 0~1，越大越相似
 */
@Service
public class RagVectorService {

    private final JdbcTemplate jdbc;
    private final EmbeddingClient embedding;

    public RagVectorService(JdbcTemplate jdbc, EmbeddingClient embedding) {
        this.jdbc = jdbc;
        this.embedding = embedding;
    }

    /**
     * 摄入文件：读 → 切片 → embed → 入库
     */
    public int ingest(Path path) {
        try {
            String source = path.getFileName().toString();
            String content = Files.readString(path);
            List<String> chunks = split(content);

            // 删除旧数据
            jdbc.update("DELETE FROM documents WHERE source = ?", source);

            // 批量 Embedding
            List<float[]> embeddings = embedding.embedBatch(chunks);

            // 批量入库
            for (int i = 0; i < chunks.size(); i++) {
                jdbc.update(
                    "INSERT INTO documents (source, chunk_index, content, embedding) VALUES (?, ?, ?, ?::vector)",
                    source, i, chunks.get(i), vectorToString(embeddings.get(i))
                );
            }
            System.out.println("  [RAG] 摄入完成: " + source + " → " + chunks.size() + " 块");
            return chunks.size();
        } catch (Exception e) {
            throw new RuntimeException("RAG 摄入失败: " + e.getMessage(), e);
        }
    }

    /**
     * 语义搜索。
     * @param query  搜索查询
     * @param topK   返回前 K 个结果
     * @param threshold 相似度阈值，低于此值的结果过滤掉
     */
    public SearchResult search(String query, int topK, double threshold) {
        if (count() == 0) return new SearchResult(List.of(), "知识库为空");

        float[] queryVec = embedding.embed(query);

        String sql = """
            SELECT source, chunk_index, content,
                   ROUND((1 - (embedding <=> ?::vector))::numeric, 4) AS similarity
            FROM documents
            WHERE 1 - (embedding <=> ?::vector) > ?
            ORDER BY embedding <=> ?::vector
            LIMIT ?
            """;

        String vecStr = vectorToString(queryVec);
        String executableSql = sql.replace("\n", " ").trim()
                .replaceFirst("\\?::vector", "'" + vecStr + "'::vector")
                .replaceFirst("\\?::vector", "'" + vecStr + "'::vector")
                .replaceFirst("\\?", String.valueOf(threshold))
                .replaceFirst("\\?::vector", "'" + vecStr + "'::vector")
                .replaceFirst("\\?", String.valueOf(topK));
        System.out.println("  [RAG] SQL: " + executableSql);

        List<DocumentHit> hits = jdbc.query(sql,
                (ResultSet rs, int rowNum) -> new DocumentHit(
                        rs.getString("source"),
                        rs.getInt("chunk_index"),
                        rs.getString("content"),
                        rs.getDouble("similarity")
                ),
                vectorToString(queryVec), vectorToString(queryVec), threshold,
                vectorToString(queryVec), topK);

        if (hits.isEmpty()) return new SearchResult(List.of(), "未找到相关内容（相似度阈值 " + threshold + "）");

        StringBuilder sb = new StringBuilder();
        sb.append("语义检索结果（查询: \"").append(query).append("\", 共 ").append(hits.size()).append(" 条）:\n\n");
        for (int i = 0; i < hits.size(); i++) {
            DocumentHit h = hits.get(i);
            sb.append("--- ").append(i + 1)
              .append(" 来源: ").append(h.source)
              .append(" 块#" + h.chunkIndex)
              .append(" 相似度: ").append(String.format("%.2f", h.similarity))
              .append(" ---\n").append(h.content).append("\n\n");
        }
        return new SearchResult(
                hits.stream().map(h -> h.content).collect(Collectors.toList()),
                sb.toString().trim()
        );
    }

    /** 总块数 */
    public long count() {
        Long c = jdbc.queryForObject("SELECT COUNT(*) FROM documents", Long.class);
        return c != null ? c : 0;
    }

    /** 列出所有索引的文档 */
    public List<Map<String, Object>> listSources() {
        return jdbc.queryForList(
            "SELECT source, COUNT(*) AS chunks, MAX(created_at) AS last_ingested FROM documents GROUP BY source ORDER BY last_ingested DESC"
        );
    }

    /** 删除指定文档的所有块 */
    public void delete(String source) {
        jdbc.update("DELETE FROM documents WHERE source = ?", source);
    }

    // ===== 内部方法 =====

    /** 固定长度切片（后续可升级为语义切片） */
    private List<String> split(String text) {
        List<String> chunks = new ArrayList<>();
        int chunkSize = 500, overlap = 50, start = 0, idx = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            if (end < text.length()) {
                int bp = text.lastIndexOf("\n\n", end);
                if (bp > start + 250) end = bp;
                else { bp = text.lastIndexOf("\n", end); if (bp > start + 250) end = bp; }
            }
            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) chunks.add(chunk);
            start = end - overlap;
            if (end >= text.length()) break;
            idx++;
        }
        return chunks;
    }

    /** float[] → pgvector 字符串格式 '[0.1, 0.2, ...]' */
    private String vectorToString(float[] vec) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vec[i]);
        }
        return sb.append("]").toString();
    }

    // ===== 数据类 =====

    public record SearchResult(List<String> chunks, String text) {}
    private record DocumentHit(String source, int chunkIndex, String content, double similarity) {}
}
