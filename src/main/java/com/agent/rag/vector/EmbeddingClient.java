package com.agent.rag.vector;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * BGE Embedding 客户端 — 通过 HTTP 调本地 BGE Server。
 * 和 Spring AI EmbeddingModel 接口等价，但直接操作 RestClient，不依赖 AI 框架。
 */
@Component
public class EmbeddingClient {

    private final RestClient client;
    private final ObjectMapper json = new ObjectMapper();

    public EmbeddingClient(RestClient.Builder builder) {
        this.client = builder.baseUrl("http://127.0.0.1:9999").build();
    }

    /** 将文本转为 1024 维向量 */
    public float[] embed(String text) {
        return embedBatch(List.of(text)).get(0);
    }

    /**
     * 批量向量化。
     * BGE Server 不可用时自动降级为 hash 测试模式，保证开发链路不断。
     */
    public List<float[]> embedBatch(List<String> texts) {
        try {
            String body = json.writeValueAsString(Map.of(
                    "model", "bge-large-zh-v1.5",
                    "input", texts
            ));

            String resp = client.post()
                    .uri("/v1/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            OpenAIEmbeddingResponse response = json.readValue(resp, OpenAIEmbeddingResponse.class);
            List<float[]> result = new ArrayList<>();
            for (OpenAIEmbeddingResponse.EmbeddingData item : response.data) {
                result.add(item.embedding);
            }
            return result;

        } catch (Exception e) {
            System.err.println("  [Embedding] BGE Server 不可用: " + e.getMessage());
            List<float[]> result = new ArrayList<>();
            for (String text : texts) result.add(pseudoEmbed(text));
            return result;
        }
    }

    /** 伪 Embedding：用文本 hash 生成确定性向量 */
    private float[] pseudoEmbed(String text) {
        float[] vec = new float[1024];
        int h = text.hashCode();
        for (int i = 0; i < 1024; i++) {
            vec[i] = ((h * (i + 1) + i * 31) % 1000) / 1000f;
        }
        return vec;
    }

    public int dimensions() { return 1024; }

    // ===== OpenAI 兼容的 Embedding 响应模型 =====

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class OpenAIEmbeddingResponse {
        public List<EmbeddingData> data;
        public String model;

        @JsonIgnoreProperties(ignoreUnknown = true)
        static class EmbeddingData {
            public int index;
            @JsonProperty("embedding")
            public float[] embedding;
        }
    }
}
