package com.agent.memory;

import com.agent.trace.TracingService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Memory @Tool — 暴露给 Agent。
 * sessionId 和 userId 通过 ThreadLocal 传递（由 ChatController 在请求前设置）。
 */
@Component
public class MemoryTools {

    private final WorkingMemory working;
    private final LongTermMemory longTerm;

    /** 当前请求上下文 */
    private static final ThreadLocal<RequestContext> ctx = new ThreadLocal<>();

    public MemoryTools(WorkingMemory w, LongTermMemory l) { this.working = w; this.longTerm = l; }

    /** ChatController 在每次请求前调用 */
    public static void setContext(String sessionId, Long userId) {
        ctx.set(new RequestContext(sessionId, userId));
    }

    public static void clearContext() { ctx.remove(); }

    @Tool(description = "记录工作笔记。用户说记一下/帮我记/提醒我时必须调用此工具")
    public String noteToSelf(@ToolParam(description = "笔记内容") String note) {
        TracingService.recordToolCall("noteToSelf");
        RequestContext c = ctx.get();
        if (c == null) return "错误：没有活跃会话";
        System.out.println("  [Tool] noteToSelf called: " + note);
        return working.note(c.sessionId, c.userId, note);
    }

    @Tool(description = "查看之前记录的所有工作笔记。用户说查看笔记/历史记录时必须调用")
    public String readMyNotes() {
        TracingService.recordToolCall("readMyNotes");
        RequestContext c = ctx.get();
        if (c == null) return "错误：没有活跃会话";
        System.out.println("  [Tool] readMyNotes called");
        return working.readAll(c.sessionId);
    }

    @Tool(description = "查询已知的用户信息")
    public String getUserProfile() {
        TracingService.recordToolCall("getUserProfile");
        RequestContext c = ctx.get();
        if (c == null) return "暂无用户信息";
        System.out.println("  [Tool] getUserProfile called");
        var p = longTerm.allProfiles(c.userId);
        return p.isEmpty() ? "暂无用户信息" : String.join("\n", p);
    }

    private record RequestContext(String sessionId, Long userId) {}
}
