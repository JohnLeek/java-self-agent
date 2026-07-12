package com.agent.controller;

import com.agent.dao.Message;
import com.agent.dao.Session;
import com.agent.repository.MessageRepository;
import com.agent.repository.SessionRepository;
import com.agent.service.ChatService;
import com.agent.service.SessionService;
import com.agent.trace.TracingService;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * SSE 流式聊天接口。
 * 只处理 HTTP 层：参数解析、SSE 推送、会话管理。
 * 业务逻辑全部在 ChatService / SessionService 中。
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    private final ExecutorService executor = new ThreadPoolExecutor(
            4, 8, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),
            new ThreadPoolExecutor.CallerRunsPolicy());

    @Resource private ChatService chatService;
    @Resource private SessionService sessionService;
    @Resource private SessionRepository sessionRepo;
    @Resource private MessageRepository messageRepo;
    @Resource private com.agent.repository.AgentTraceRepository traceRepo;
    @Resource private com.agent.agent.ExecutionController executionController;

    /** 中断执行 */
    @PostMapping("/chat/stop")
    public Map<String, String> stop(@RequestParam String sessionId) {
        executionController.requestStop(sessionId);
        return Map.of("status", "stopped");
    }

    /** 查询某轮 trace */
    @GetMapping("/sessions/{id}/traces")
    public List<com.agent.dao.AgentTrace> getTraces(@PathVariable String id, @RequestParam(defaultValue = "1") int round) {
        return traceRepo.findBySessionIdAndRoundOrderByStepOrderAsc(id, round);
    }

    /** 历史会话列表 */
    @GetMapping("/sessions")
    public List<Map<String, Object>> listSessions() {
        return sessionRepo.findByUserIdOrderByUpdatedAtDesc(1L).stream()
                .map(s -> Map.<String, Object>of(
                        "id", s.getId(),
                        "title", s.getTitle(),
                        "updatedAt", s.getUpdatedAt().toString(),
                        "messageCount", messageRepo.countBySessionId(s.getId())
                )).toList();
    }

    /** 删除会话 */
    @DeleteMapping("/sessions/{id}")
    public Map<String, String> deleteSession(@PathVariable String id) {
        messageRepo.findBySessionIdOrderByCreatedAtAsc(id).forEach(m -> messageRepo.delete(m));
        sessionRepo.deleteById(id);
        return Map.of("status", "deleted");
    }

    /** 历史会话消息详情 */
    @GetMapping("/sessions/{id}/messages")
    public List<Map<String, Object>> getMessages(@PathVariable String id) {
        return messageRepo.findBySessionIdOrderByCreatedAtAsc(id).stream()
                .map(m -> Map.<String, Object>of(
                        "id", m.getId(),
                        "role", m.getRole(),
                        "content", m.getContent(),
                        "toolName", m.getToolName() != null ? m.getToolName() : "",
                        "createdAt", m.getCreatedAt().toString()
                )).toList();
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @PostMapping("/session")
    public Map<String, String> createSession() {
        Session s = sessionService.create("新会话");
        return Map.of("sessionId", s.getId());
    }

    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam String message,
                             @RequestParam(defaultValue = "") String sessionId) {
        SseEmitter emitter = new SseEmitter(300_000L);

        final String sid;
        if (sessionId.isEmpty()) {
            Session s = sessionService.create(message);
            sid = s.getId();
            send(emitter, "session", "{\"sessionId\":\"" + sid + "\"}");
        } else {
            sid = sessionId;
        }

        executor.submit(() -> {
            try {
                TracingService.Trace trace = chatService.getTracing().startTrace(message);
                TracingService.Trace.Span llmSpan = trace.addSpan("LLM", "chat");
                StringBuilder fullAnswer = new StringBuilder();

                send(emitter, "trace", "{\"started\":true,\"memRounds\":" + chatService.shortTermSize(sid) + "}");

                chatService.stream(sid, message)
                        .doOnNext(c -> {
                            if (c.startsWith("STEP:")) {
                                send(emitter, "step", c.substring(5));
                            } else {
                                fullAnswer.append(c);
                                send(emitter, "chunk", c);
                            }
                        })
                        .doOnComplete(() -> {
                            llmSpan.finish("ok");
                            String answer = fullAnswer.toString();
                            chatService.afterComplete(sid, message, answer);
                            sessionService.touch(sid);
                            int tc = trace.toolCount();
                            trace.addSpan("TRACE", "total=" + trace.totalMs() + "ms tools=" + tc);
                            send(emitter, "trace", "{\"totalMs\":" + trace.totalMs()
                                    + ",\"tools\":" + tc
                                    + ",\"memRounds\":" + chatService.shortTermSize(sid)
                                    + ",\"spans\":" + toSpansJson(trace) + "}");
                            send(emitter, "done", "");
                            emitter.complete();
                        })
                        .doOnError(e -> {
                            send(emitter, "error", e.getMessage());
                            emitter.complete();
                        })
                        .subscribe();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private void send(SseEmitter e, String name, String data) {
        try {
            e.send(SseEmitter.event().name(name).data(data));
        } catch (IOException ignored) {
        }
    }

    private String toSpansJson(TracingService.Trace trace) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < trace.spans.size(); i++) {
            var s = trace.spans.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"type\":\"").append(s.type).append("\",\"name\":\"").append(s.name)
                    .append("\",\"ms\":").append(s.durationMs).append(",\"detail\":\"")
                    .append(s.detail != null ? s.detail : "").append("\"}");
        }
        return sb.append("]").toString();
    }
}
