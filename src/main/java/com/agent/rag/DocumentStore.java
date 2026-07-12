package com.agent.rag;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/** 内存文档存储 + 关键词检索 */
@Component
public class DocumentStore {

    private final Map<String, Chunk> map = new ConcurrentHashMap<>();
    private final AtomicInteger idGen = new AtomicInteger(1);

    public void add(Chunk c) { map.put(c.id(), c); }
    public Collection<Chunk> all() { return map.values(); }
    public int size() { return map.size(); }
    public String nextId() { return "c" + idGen.getAndIncrement(); }

    public SearchResult search(String query, int topK) {
        if (map.isEmpty()) return new SearchResult(List.of(), 0.0, "无结果");
        Set<String> qTokens = tokenize(query);
        List<Scored> scored = new ArrayList<>();
        for (Chunk c : map.values()) {
            double s = score(c.content(), qTokens);
            if (s > 0) scored.add(new Scored(c, s));
        }
        scored.sort((a, b) -> Double.compare(b.score, a.score));
        List<Scored> top = scored.stream().limit(topK).toList();
        double avg = top.stream().mapToDouble(s -> s.score).average().orElse(0);
        StringBuilder sb = new StringBuilder();
        sb.append("查询: \"").append(query).append("\" 结果 ").append(top.size()).append(" 条 平均相关 ").append(String.format("%.2f", avg)).append("\n\n");
        for (int i = 0; i < top.size(); i++) {
            Scored s = top.get(i);
            sb.append("--- ").append(i + 1).append(" 来源: ").append(s.chunk.source()).append(" 相关: ").append(String.format("%.2f", s.score)).append("\n");
            sb.append(s.chunk.content()).append("\n\n");
        }
        return new SearchResult(top.stream().map(s -> s.chunk).toList(), avg, sb.toString().trim());
    }

    private Set<String> tokenize(String t) {
        return Arrays.stream(t.toLowerCase().replaceAll("[，。！？、；：（）\"'\\[\\]{}|,.;:!?()`~@#$%^&*+=<>/\\\\-]", " ").split("\\s+"))
                .filter(w -> w.length() > 1 && !STOP.contains(w)).collect(Collectors.toSet());
    }

    private double score(String content, Set<String> qt) {
        String[] ws = content.toLowerCase().replaceAll("[，。！？、；：（）\"'\\[\\]{}|,.;:!?()`~@#$%^&*+=<>/\\\\-]", " ").split("\\s+");
        int m = 0; for (String w : ws) if (qt.contains(w)) m++;
        return ws.length == 0 ? 0 : (double) m / ws.length * Math.min(1.0, (double) m / qt.size());
    }

    private record Scored(Chunk chunk, double score) {}

    public record Chunk(String id, String source, String content, int index) {}
    public record SearchResult(List<Chunk> chunks, double avgScore, String text) {}

    private static final Set<String> STOP = Set.of("的","了","在","是","我","有","和","就","不","人","都","一","the","a","an","is","are","to","of","in","for","and","this","that","it");
}
