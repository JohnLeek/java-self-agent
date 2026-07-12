package com.agent.agent;

import com.agent.dao.AgentTrace;
import com.agent.repository.AgentTraceRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 真正的多 Agent 编排器 — Plan → Execute → Summarize。
 * 每步写入 agent_traces 表，支持中断、恢复、回放。
 */
@Component
public class OrchestratorAgent {

    private ChatClient planner;
    private ChatClient summarizer;

    @Resource
    private SpecialistAgent searchAgent;
    @Resource
    private SpecialistAgent toolAgent;
    @Resource
    private SpecialistAgent memoryAgent;
    @Resource
    private AgentTraceRepository traceRepo;
    @Resource
    private ExecutionController executionCtrl;
    @Resource
    private ObjectProvider<ChatClient.Builder> builderProvider;

    private int roundCounter = 0;

    @PostConstruct
    public void init() {
        this.planner = builderProvider.getObject().build();
        this.summarizer = builderProvider.getObject().build();
    }

    public Flux<String> process(String message, String sessionId, String memoryContext, String profileContext) {
        return Flux.create(sink -> {
            int round = ++roundCounter;
            int[] step = {0};
            try {
                executionCtrl.clear(sessionId);
                // ===== Step 1: Plan =====
                String plan = plan(message);
                trace(sessionId, round, step[0]++, "PLAN", null, message, plan, "DONE");

                // ===== Step 2: Execute =====
                StringBuilder context = new StringBuilder();
                context.append("用户消息: ").append(message).append("\n\n");
                String lastResult = "";

                if (plan.contains("SEARCH") || plan.contains("搜索") || plan.contains("知识库") || plan.contains("检索")) {
                    if (executionCtrl.isStopped(sessionId)) {
                        System.out.println("  [Orchestrator] 检测到中断信号，停止执行");
                        trace(sessionId, round, step[0]++, "AGENT_CALL", "搜索专家", null, "用户中断", "INTERRUPTED");
                        sink.complete(); return;
                    }
                    emit(sink, "agent_call", "🔍 调用搜索专家", "在知识库中语义检索...");
                    String searchTask = "搜索知识库: " + message;
                    trace(sessionId, round, step[0]++, "AGENT_CALL", "搜索专家", searchTask, null, "RUNNING");
                    lastResult = searchAgent.delegate(searchTask);
                    trace(sessionId, round, step[0] - 1, "AGENT_CALL", "搜索专家", searchTask, lastResult, "DONE");
                    emit(sink, "thinking", "搜索专家思考结果",
                            lastResult.length() > 150 ? lastResult.substring(0, 150) + "..." : lastResult);
                    context.append("【知识库检索结果】\n").append(lastResult).append("\n\n");

                    // 写入搜索结果到 messages 表（tool role）
                    trace(sessionId, round, step[0]++, "TOOL_CALL", "搜索专家", "search", lastResult, "DONE");
                }

                if (plan.contains("TOOL") || plan.contains("计算") || plan.contains("翻译") || plan.contains("掷骰子")) {
                    if (executionCtrl.isStopped(sessionId)) { sink.complete(); return; }
                    emit(sink, "agent_call", "🔧 调用工具专家", "执行计算/翻译/脚本等操作");
                    String toolTask = (!lastResult.isEmpty() && (plan.contains("翻译") || message.contains("翻译")))
                            ? "请将以下内容翻译:\n" + lastResult
                            : (!lastResult.isEmpty() ? "用户需求: " + message + "\n参考: " + lastResult.substring(0, Math.min(500, lastResult.length())) : message);
                    trace(sessionId, round, step[0]++, "AGENT_CALL", "工具专家", toolTask, null, "RUNNING");
                    String toolResult = toolAgent.delegate(toolTask);
                    trace(sessionId, round, step[0] - 1, "AGENT_CALL", "工具专家", toolTask, toolResult, "DONE");
                    emit(sink, "thinking", "工具专家执行结果",
                            toolResult.length() > 150 ? toolResult.substring(0, 150) + "..." : toolResult);
                    context.append("【工具执行结果】\n").append(toolResult).append("\n\n");
                }

                if (plan.contains("MEMORY") || plan.contains("笔记") || plan.contains("记忆")) {
                    emit(sink, "agent_call", "📝 调用记忆专家", "查询笔记和用户信息");
                    trace(sessionId, round, step[0]++, "AGENT_CALL", "记忆专家", message, null, "RUNNING");
                    String memResult = memoryAgent.delegate(message);
                    trace(sessionId, round, step[0] - 1, "AGENT_CALL", "记忆专家", message, memResult, "DONE");
                    context.append("【记忆查询结果】\n").append(memResult).append("\n\n");
                }

                // ===== Step 3: Summarize =====
                emit(sink, "thinking", "汇总思考", "基于搜索结果和工具结果生成最终回答...");
                trace(sessionId, round, step[0]++, "SUMMARY", null, context.toString(), null, "RUNNING");

                String summaryPrompt = "你是回答汇总助手。不要编造信息。";
                if (profileContext != null && !profileContext.isEmpty()) summaryPrompt += "\n" + profileContext;
                if (memoryContext != null && !memoryContext.isEmpty()) summaryPrompt += "\n【对话上下文】\n" + memoryContext;

                StringBuilder answer = new StringBuilder();
                summarizer.prompt().system(summaryPrompt).user(context.toString())
                        .stream().content()
                        .doOnNext(c -> { answer.append(c); sink.next(c); })
                        .doOnComplete(() -> {
                            trace(sessionId, round, step[0] - 1, "SUMMARY", null, context.toString(), answer.toString(), "DONE");
                            sink.complete();
                        })
                        .doOnError(sink::error)
                        .blockLast();

            } catch (Exception e) {
                trace(sessionId, round, step[0], "SUMMARY", null, null, e.getMessage(), "FAILED");
                sink.error(e);
            }
        });
    }

    private String plan(String message) {
        return planner.prompt()
                .system("判断需要哪些专家，只输出标签（逗号分隔）。SEARCH(查知识库) TOOL(计算/翻译/脚本) MEMORY(笔记/用户)。都不需要输出NONE。")
                .user(message).call().content().trim();
    }

    private void trace(String sessionId, int round, int order, String type,
                        String agent, String input, String output, String status) {
        try {
            AgentTrace t = AgentTrace.create(sessionId, round, order, type, agent, input, output, status);
            traceRepo.save(t);
        } catch (Exception e) { System.err.println("[Trace] 写入失败: " + e.getMessage()); }
    }

    private void emit(reactor.core.publisher.FluxSink<String> sink, String phase, String title, String content) {
        String json = String.format("{\"phase\":\"%s\",\"title\":\"%s\",\"content\":\"%s\"}",
                phase, escape(title), escape(content));
        sink.next("STEP:" + json);
    }

    private String escape(String s) { return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " "); }
}
