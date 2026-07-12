package com.agent.service;

import com.agent.dao.Session;
import com.agent.repository.SessionRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * 会话服务 —— 封装 sessions 表的业务操作。
 */
@Service
public class SessionService {

    private final SessionRepository sessionRepo;
    private final ChatClient.Builder titleBuilder;
    private static final Long DEFAULT_USER_ID = 1L;

    public SessionService(SessionRepository sessionRepo,
                          ObjectProvider<ChatClient.Builder> builderProvider) {
        this.sessionRepo = sessionRepo;
        this.titleBuilder = builderProvider.getObject();
    }

    /** 创建新会话，标题先用默认值 */
    public Session create(String firstMessage) {
        String draftTitle = firstMessage != null
                ? firstMessage.substring(0, Math.min(50, firstMessage.length()))
                : "新会话";
        return sessionRepo.save(Session.create(DEFAULT_USER_ID, draftTitle));
    }

    /** 首轮对话完成后异步生成标题 */
    public void generateTitleAsync(String sessionId, String firstMessage) {
        CompletableFuture.runAsync(() -> {
            try {
                String title = titleBuilder.build()
                        .prompt()
                        .system("你是一个标题生成助手。根据用户的第一条消息生成一个简短的会话标题（不超过20字）。只输出标题，不要引号、不要解释。")
                        .user(firstMessage)
                        .call()
                        .content()
                        .replaceAll("[\"'《》]", "")
                        .trim();

                if (title.length() > 20) title = title.substring(0, 20);
                final String finalTitle = title;

                sessionRepo.findById(sessionId).ifPresent(s -> {
                    s.setTitle(finalTitle);
                    sessionRepo.save(s);
                });
                System.out.println("  [Session] 标题已生成: " + finalTitle);
            } catch (Exception e) {
                System.err.println("  [Session] 标题生成失败: " + e.getMessage());
            }
        });
    }

    /** 更新会话时间 */
    public void touch(String sessionId) {
        sessionRepo.findById(sessionId).ifPresent(s -> {
            s.touch();
            sessionRepo.save(s);
        });
    }
}
