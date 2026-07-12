package com.agent.service;

import com.agent.memory.*;
import com.agent.rag.RagTool;
import com.agent.agent.OrchestratorAgent;
import com.agent.agent.OrchestratorTools;
import com.agent.rag.vector.RagVectorService;
import com.agent.repository.MessageRepository;
import com.agent.skill.SkillRegistry;
import com.agent.skill.external.ImportedSkillRegistry;
import com.agent.skill.external.SkillRouter;
import com.agent.trace.TracingService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 聊天服务 —— 编排 LLM 调用、工具注册、记忆更新、追踪。
 *
 * 职责：
 *   1. 组装 ChatClient（工具 + system prompt）
 *   2. 注入三层记忆上下文
 *   3. 流式调用 LLM
 *   4. 调用完成后更新短期/长期记忆
 *
 * Controller 只负责 HTTP 层面的 SSE 推送，业务逻辑全部在此。
 */
@Service
public class ChatService {

    private static final Long DEFAULT_USER_ID = 1L;

    private final SkillRegistry skills;
    private final ShortTermMemory shortTerm;
    private final LongTermMemory longTerm;
    private final TracingService tracing;
    private final ObjectProvider<ChatClient.Builder> builderProvider;
    private final MemoryTools memoryTools;
    private final RagTool ragTool;
    private final MessageRepository messageRepo;
    private final SessionService sessionService;
    private final SkillRouter skillRouter;
    private final ImportedSkillRegistry importedRegistry;
    private final RagVectorService ragVectorService;
    private final OrchestratorAgent orchestratorAgent;

    @Value("${agent.mode:single}")
    private String agentMode;

    public ChatService(SkillRegistry skills, ShortTermMemory st, LongTermMemory lt,
                       TracingService tr, ObjectProvider<ChatClient.Builder> bp,
                       MemoryTools mt, RagTool rt,
                       MessageRepository messageRepo, SessionService sessionService,
                       SkillRouter skillRouter, ImportedSkillRegistry importedRegistry,
                       RagVectorService ragVectorService,
                       OrchestratorAgent orchestratorAgent) {
        this.skills = skills; this.shortTerm = st; this.longTerm = lt;
        this.tracing = tr; this.builderProvider = bp;
        this.memoryTools = mt; this.ragTool = rt;
        this.messageRepo = messageRepo;
        this.sessionService = sessionService;
        this.skillRouter = skillRouter;
        this.importedRegistry = importedRegistry;
        this.ragVectorService = ragVectorService;
        this.orchestratorAgent = orchestratorAgent;
    }

    /** 构建 ChatClient。仅 single 模式使用；multi 模式走 OrchestratorAgent.process() */
    private ChatClient buildChatClient() {
        var builder = builderProvider.getObject()
                .defaultTools(skills.enabledBeans().toArray())
                .defaultTools(memoryTools)
                .defaultTools(ragTool);

        var externalCallbacks = importedRegistry.toToolCallbacks(DEFAULT_USER_ID);
        System.out.println("[ChatService] Single模式：内部工具 + 外部 " + externalCallbacks.size() + " 个回调（含MCP）");
        System.out.println("[ChatService] 工具列表: "
                + externalCallbacks.stream().map(c -> c.getName()).toList());
        if (!externalCallbacks.isEmpty()) {
            builder = builder.defaultFunctions(externalCallbacks.toArray(new org.springframework.ai.model.function.FunctionCallback[0]));
        }

        return builder.build();
    }

    /** 构建完整的 system prompt + 记忆上下文 + RAG 自动检索 */
    private String buildSystemPrompt(String sessionId, String userMessage) {
        if ("multi".equals(agentMode)) {
            return buildMultiAgentPrompt(sessionId, userMessage);
        }
        return buildSingleAgentPrompt(sessionId, userMessage);
    }

    private String buildMultiAgentPrompt(String sessionId, String userMessage) {
        String ctx = shortTerm.context(sessionId);
        StringBuilder sb = new StringBuilder();
        sb.append("""
                你是 Leader Agent，通过 delegation 工具协调专家团队。
                工作流程：
                1. 分析用户意图，判断需要哪个专家
                2. 调 delegateSearch / delegateTools / delegateMemory 分派任务
                3. 危险操作（删除文档、写文件、执行脚本）→ 必须先调 requestApproval 获得批准
                4. 汇总结果回答用户
                限制：delegate 失败不要重试，直接告诉用户。
                """);
        if (!ctx.isEmpty()) sb.append("【对话上下文】\n").append(ctx);
        return sb.toString();
    }

    private String buildSingleAgentPrompt(String sessionId, String userMessage) {
        String profile = longTerm.retrieve(DEFAULT_USER_ID);
        String ctx = shortTerm.context(sessionId);

        // 自动检索知识库（不需要用户显式调用 search tool）
        StringBuilder ragCtx = new StringBuilder();
        if (ragVectorService.count() > 0) {
            var r = ragVectorService.search(userMessage, 3, 0.5);
            System.out.println("[ChatService] RAG 自动检索: " + r.chunks().size() + " 条结果");
            if (!r.chunks().isEmpty()) {
                ragCtx.append("【知识库自动检索】\n");
                ragCtx.append(r.text()).append("\n");
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("""
                工具使用规则（必须遵守）：
                1. 用户要求"记一下/帮我记/提醒/记录"→必须调noteToSelf，不要用文字代替
                2. 用户要求"查看笔记/历史记录/之前记了什么"→必须调readMyNotes
                3. 用户问数学计算→必须调calculator，不要心算
                4. 用户问知识库问题→调search
                5. 用户要求翻译→调translation
                6. 用户要求随机数/掷骰子→调dice
                7. 用户问个人信息→调getUserProfile

                关键：执行操作后只回复工具返回的结果，不要自己编造内容。
                例如用户说"帮我记买牛奶"，你应该直接调noteToSelf("买牛奶")，
                然后回复"已记录"，而不是说"好的我帮你记下了"却不调工具。
                """);
        if (!profile.isEmpty()) sb.append(profile).append("\n");
        if (!ragCtx.isEmpty()) sb.append(ragCtx).append("\n");
        if (!ctx.isEmpty()) sb.append("【对话上下文】\n").append(ctx);
        return sb.toString();
    }

    /**
     * 流式聊天。
     * @return Flux<String> 文本流
     */
    public Flux<String> stream(String sessionId, String message) {
        MemoryTools.setContext(sessionId, DEFAULT_USER_ID);
        tracing.startTrace(message);

        // 多 Agent 模式：Orchestrator Plan → Execute → Summarize
        if ("multi".equals(agentMode)) {
            System.out.println("[ChatService] 多 Agent 模式：Orchestrator Plan → Execute → Summarize");
            String memCtx = shortTerm.context(sessionId);
            String profile = longTerm.retrieve(DEFAULT_USER_ID);
            String promptSkills = skillRouter.routePromptSkills(DEFAULT_USER_ID, message);
            return orchestratorAgent.process(message, sessionId, memCtx, profile + promptSkills)
                    .doOnComplete(() -> MemoryTools.clearContext())
                    .doOnError(e -> MemoryTools.clearContext());
        }

        // 单 Agent 模式
        String promptSkills = skillRouter.routePromptSkills(DEFAULT_USER_ID, message);

        return buildChatClient().prompt()
                .system(buildSystemPrompt(sessionId, message) + promptSkills)
                .user(message)
                .stream()
                .content()
                .doOnComplete(() -> {
                    MemoryTools.clearContext();
                })
                .doOnError(e -> MemoryTools.clearContext());
    }

    /**
     * 流式调用完成后持久化记忆。
     * Controller 收集完文本后调用此方法。
     */
    public void afterComplete(String sessionId, String userMessage, String fullAnswer) {
        shortTerm.record(sessionId, userMessage, fullAnswer);
        longTerm.extractAndStore(DEFAULT_USER_ID, userMessage, null);

        // 首轮对话完成 → 异步生成 LLM 标题
        if (messageRepo.countBySessionId(sessionId) == 2) {
            sessionService.generateTitleAsync(sessionId, userMessage);
        }
    }

    public TracingService getTracing() { return tracing; }
    public int shortTermSize(String sessionId) { return shortTerm.size(sessionId); }
}
