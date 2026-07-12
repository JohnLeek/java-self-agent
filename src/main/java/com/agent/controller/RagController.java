package com.agent.controller;

import com.agent.rag.vector.RagVectorService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * RAG 文档管理接口。
 */
@RestController
@RequestMapping("/api/rag")
public class RagController {

    @Resource private RagVectorService ragService;

    /** 列出已索引的文档 */
    @GetMapping("/sources")
    public List<Map<String, Object>> sources() {
        return ragService.listSources();
    }

    /** 摄入文件 */
    @PostMapping("/ingest")
    public Map<String, Object> ingest(@RequestParam String path) {
        int n = ragService.ingest(Path.of(path));
        return Map.of("status", "ok", "chunks", n);
    }

    /** 上传文件并摄入 */
    @PostMapping("/upload")
    public Map<String, Object> upload(@RequestParam MultipartFile file) throws IOException {
        Path tmp = Files.createTempFile("rag-", "-" + file.getOriginalFilename());
        file.transferTo(tmp);
        int n = ragService.ingest(tmp);
        try { Files.deleteIfExists(tmp); } catch (Exception ignored) {}
        return Map.of("status", "ok", "chunks", n, "source", file.getOriginalFilename());
    }

    /** 删除文档 */
    @DeleteMapping("/doc")
    public Map<String, String> delete(@RequestParam String source) {
        ragService.delete(source);
        return Map.of("status", "deleted");
    }

    /** 知识库状态 */
    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("totalChunks", ragService.count(), "sources", ragService.listSources().size());
    }
}
