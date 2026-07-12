package com.agent.controller;

import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Human-in-the-Loop 审批接口。
 *
 * 流程：
 *   1. Agent 调 requestApproval tool → 存入 pending → 阻塞等待
 *   2. 后端发送 SSE event: approval_required 给前端
 *   3. 前端弹确认框 → 用户点确认/拒绝 → 调 /api/approval/{id}/approve 或 reject
 *   4. CompletableFuture 被 resolve → Agent 继续执行
 */
@RestController
@RequestMapping("/api/approval")
public class ApprovalController {

    private static final ConcurrentHashMap<String, CompletableFuture<Boolean>> pending = new ConcurrentHashMap<>();

    /** Agent tool 调用：阻塞等待审批结果 */
    public static String requestApproval(String action) {
        String id = java.util.UUID.randomUUID().toString().substring(0, 8);
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        pending.put(id, future);

        try {
            Boolean approved = future.get(60, TimeUnit.SECONDS);
            pending.remove(id);
            return approved ? "APPROVED: " + action : "DENIED: " + action;
        } catch (Exception e) {
            pending.remove(id);
            return "DENIED: 审批超时";
        }
    }

    /** 检查是否有待审批项 */
    @GetMapping("/pending")
    public Map<String, Object> pending() {
        return Map.of("count", pending.size(), "ids", pending.keySet());
    }

    @PostMapping("/{id}/approve")
    public Map<String, String> approve(@PathVariable String id) {
        CompletableFuture<Boolean> f = pending.get(id);
        if (f != null) f.complete(true);
        return Map.of("status", "approved");
    }

    @PostMapping("/{id}/reject")
    public Map<String, String> reject(@PathVariable String id) {
        CompletableFuture<Boolean> f = pending.get(id);
        if (f != null) f.complete(false);
        return Map.of("status", "rejected");
    }
}
