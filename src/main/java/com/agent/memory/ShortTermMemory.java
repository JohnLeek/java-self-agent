package com.agent.memory;

import com.agent.dao.Message;
import com.agent.repository.MessageRepository;
import com.agent.repository.SessionRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 短期记忆 —— Token 预算驱动的滑动窗口 + LLM 摘要压缩。
 *
 * 压缩触发不看消息轮数，看 Token 数量。
 * 每个模型有不同的上下文窗口，按比例分配预算。
 */
@Component
public class ShortTermMemory {

    private static final Map<String, Integer> MODEL_CONTEXT = Map.of(
        "deepseek-chat", 64_000, "deepseek-v4", 1_000_000,
        "gpt-4o", 128_000, "gpt-4o-mini", 128_000
    );

    private final MessageRepository messageRepo;
    private final SessionRepository sessionRepo;
    private final ChatClient.Builder summaryBuilder;
    private final int tokenBudget;

    public ShortTermMemory(MessageRepository messageRepo,
                           SessionRepository sessionRepo,
                           ObjectProvider<ChatClient.Builder> builderProvider,
                           @Value("${spring.ai.openai.chat.options.model}") String model) {
        this.messageRepo = messageRepo;
        this.sessionRepo = sessionRepo;
        this.summaryBuilder = builderProvider.getObject();
        int ctx = MODEL_CONTEXT.getOrDefault(model, 64_000);
        this.tokenBudget = (int)(ctx * 0.5); // 上下文的 50% 用于短期记忆
        System.out.println("[ShortTermMemory] 模型: " + model + " 上下文: " + ctx + " Token预算: " + tokenBudget);
    }

    /** 记录一轮对话，Token 超过预算时触发压缩 */
    public void record(String sessionId, String userMsg, String agentMsg) {
        messageRepo.save(Message.user(sessionId, userMsg));
        messageRepo.save(Message.agent(sessionId, agentMsg));

        // 首轮对话不压缩
        long total = messageRepo.countBySessionId(sessionId);
        if (total <= 2) return;

        // Token 超过预算 → 压缩
        long currentTokens = estimateSessionTokens(sessionId);
        if (currentTokens > tokenBudget) {
            compressAsync(sessionId);
        }
    }

    /** 构建上下文：历史摘要 + Token 预算内的最近消息 */
    public String context(String sessionId) {
        List<Message> msgs = messageRepo.findBySessionIdOrderByCreatedAtAsc(sessionId);
        if (msgs.isEmpty()) return "";

        // 从最新消息往前累加 Token，不超过预算
        long tokenCount = 0;
        int cutoff = msgs.size();
        for (int i = msgs.size() - 1; i >= 0; i--) {
            tokenCount += estimateTokens(msgs.get(i).getContent());
            if (tokenCount > tokenBudget) { cutoff = i + 1; break; }
        }
        List<Message> recent = msgs.subList(cutoff, msgs.size());

        StringBuilder sb = new StringBuilder();
        String summary = sessionRepo.findById(sessionId).map(s -> s.getSummary()).orElse("");
        if (!summary.isEmpty()) {
            sb.append("【历史对话摘要】\n").append(summary).append("\n\n");
        }
        sb.append("【最近对话】\n");
        for (Message m : recent) {
            if ("user".equals(m.getRole())) sb.append("用户: ").append(m.getContent()).append("\n");
            else if ("agent".equals(m.getRole())) sb.append("Agent: ").append(m.getContent()).append("\n");
        }
        return sb.toString();
    }

    public int size(String sessionId) {
        return (int) Math.ceil(messageRepo.countBySessionId(sessionId) / 2.0);
    }

    // ===== Token 估算 =====

    /** 估算单条文本的 token 数：中文 ~1.5 字/token，英文 ~4 字/token */
    private long estimateTokens(String text) {
        if (text == null) return 0;
        int chinese = 0, other = 0;
        for (char c : text.toCharArray()) {
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS)
                chinese++; else other++;
        }
        return chinese / 2 + other / 4;
    }

    /** 估算当前会话所有消息的总 Token 数 */
    private long estimateSessionTokens(String sessionId) {
        return messageRepo.findBySessionIdOrderByCreatedAtAsc(sessionId).stream()
                .mapToLong(m -> estimateTokens(m.getContent())).sum();
    }

    // ===== 异步摘要压缩 =====

    private void compressAsync(String sessionId) {
        CompletableFuture.runAsync(() -> {
            try {
                List<Message> all = messageRepo.findBySessionIdOrderByCreatedAtAsc(sessionId);
                // 取前半部分消息做摘要
                int mid = all.size() / 2;
                List<Message> oldMessages = all.subList(0, mid);

                StringBuilder conversation = new StringBuilder();
                for (Message m : oldMessages) {
                    if ("user".equals(m.getRole())) conversation.append("用户: ").append(m.getContent()).append("\n");
                    else if ("agent".equals(m.getRole())) conversation.append("Agent: ").append(m.getContent()).append("\n");
                }

                long estTokens = estimateTokens(conversation.toString());
                System.out.println("  [Memory] 开始摘要压缩，处理 " + oldMessages.size() + " 条旧消息（~" + estTokens + " tokens）...");

                String newSummary = summaryBuilder.build()
                        .prompt()
                        .system("你是一个对话摘要助手。从对话中提取关键信息：用户身份、偏好、讨论主题、重要决策、待办事项。只输出摘要内容，不超过200字。")
                        .user("请摘要以下对话：\n\n" + conversation)
                        .call().content();

                String old = sessionRepo.findById(sessionId).map(s -> s.getSummary()).orElse("");
                String merged = old.isEmpty() ? newSummary : old + "；" + newSummary;
                sessionRepo.findById(sessionId).ifPresent(s -> { s.setSummary(merged); sessionRepo.save(s); });

                System.out.println("  [Memory] 摘要已持久化: " + merged.substring(0, Math.min(100, merged.length())) + "...");
            } catch (Exception e) {
                System.err.println("  [Memory] 摘要压缩失败: " + e.getMessage());
            }
        });
    }
}
