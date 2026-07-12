package com.agent.rag;

import com.agent.rag.DocumentStore.Chunk;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 文档摄入：读文件 → 切片 → 入库 */
@Component
public class IngestionService {
    private final DocumentStore store;
    public IngestionService(DocumentStore store) { this.store = store; }

    public int ingest(Path path) throws IOException {
        String content = Files.readString(path);
        String name = path.getFileName().toString();
        List<Chunk> chunks = split(name, content);
        for (Chunk c : chunks) store.add(c);
        return chunks.size();
    }

    private List<Chunk> split(String source, String text) {
        List<Chunk> list = new ArrayList<>();
        int idx = 0, start = 0;
        while (start < text.length()) {
            int end = Math.min(start + 500, text.length());
            if (end < text.length()) {
                int bp = text.lastIndexOf("\n\n", end);
                if (bp > start + 250) end = bp;
                else { bp = text.lastIndexOf("\n", end); if (bp > start + 250) end = bp; }
            }
            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) list.add(new Chunk(store.nextId(), source, chunk, idx++));
            start = end - 50;
            if (end >= text.length()) break;
        }
        return list;
    }
}
