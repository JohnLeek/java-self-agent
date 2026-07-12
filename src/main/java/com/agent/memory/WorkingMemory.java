package com.agent.memory;

import com.agent.dao.Note;
import com.agent.repository.NoteRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 工作记忆 —— Agent 草稿纸，持久化到 notes 表。
 */
@Component
public class WorkingMemory {

    private final NoteRepository repo;

    public WorkingMemory(NoteRepository repo) {
        this.repo = repo;
    }

    /** 记录一条工作笔记 */
    public String note(String sessionId, Long userId, String content) {
        repo.save(Note.create(sessionId, userId, content));
        int count = repo.findBySessionIdOrderByCreatedAtAsc(sessionId).size();
        return "已记录 #" + count;
    }

    /** 读取当前会话的所有笔记 */
    public String readAll(String sessionId) {
        List<Note> notes = repo.findBySessionIdOrderByCreatedAtAsc(sessionId);
        if (notes.isEmpty()) return "暂无笔记";
        StringBuilder sb = new StringBuilder("工作笔记:\n");
        for (int i = 0; i < notes.size(); i++) {
            sb.append("  #").append(i + 1).append(": ").append(notes.get(i).getContent()).append("\n");
        }
        return sb.toString().trim();
    }

    public int size(String sessionId) {
        return repo.findBySessionIdOrderByCreatedAtAsc(sessionId).size();
    }
}
