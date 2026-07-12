package com.agent.rag;

import com.agent.rag.vector.RagVectorService;
import com.agent.trace.TracingService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * RAG 检索 @Tool — 基于 pgvector 语义检索。
 * 替换了旧的 InMemoryDocumentStore 关键词匹配。
 */
@Component
public class RagTool {
    private final RagVectorService ragService;

    public RagTool(RagVectorService ragService) { this.ragService = ragService; }

    @Tool(description = "搜索知识库。当用户问知识性问题时调用。返回最相关的文档片段及语义相似度分数。")
    public String search(@ToolParam(description = "搜索查询，用自然语言描述") String query) {
        TracingService.recordToolCall("search");
        System.out.println("  [Tool] search called: \"" + query + "\"");
        RagVectorService.SearchResult r = ragService.search(query, 5, 0.6);
        return r.text();
    }
}
