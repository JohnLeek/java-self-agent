package com.agent.agent;

import org.springframework.stereotype.Component;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 共享的中断控制组件。
 * ChatController 设置停止标志，OrchestratorAgent 在每步前检查。
 */
@Component
public class ExecutionController {
    private final ConcurrentHashMap<String, Boolean> stopFlags = new ConcurrentHashMap<>();

    public void requestStop(String sessionId) { stopFlags.put(sessionId, true); }
    public boolean isStopped(String sessionId) { return stopFlags.getOrDefault(sessionId, false); }
    public void clear(String sessionId) { stopFlags.remove(sessionId); }
}
