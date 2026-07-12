package com.agent.memory;

import com.agent.dao.UserProfile;
import com.agent.repository.UserProfileRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 长期记忆 —— 用户画像，持久化到 user_profiles 表。
 */
@Component
public class LongTermMemory {

    private final UserProfileRepository repo;

    public LongTermMemory(UserProfileRepository repo) {
        this.repo = repo;
    }

    /** 从用户消息中提取画像并存储（当前为关键词匹配，后续升级 LLM 提取） */
    public void extractAndStore(Long userId, String userMsg, Long sourceMessageId) {
        for (String p : new String[]{"我叫", "我是", "我喜欢", "我擅长", "我来自"}) {
            int i = userMsg.indexOf(p);
            if (i >= 0) {
                String fact = userMsg.substring(i, Math.min(i + 30, userMsg.length()))
                        .replaceAll("[，。！？\\n].*", "");
                repo.save(UserProfile.create(userId, "用户: " + fact, sourceMessageId));
            }
        }
    }

    /** 检索用户画像（当前为全量返回，后续加语义检索） */
    public String retrieve(Long userId) {
        List<UserProfile> profiles = repo.findByUserIdOrderByCreatedAtDesc(userId);
        if (profiles.isEmpty()) return "";
        return "【用户画像】\n" + profiles.stream()
                .limit(10)
                .map(UserProfile::getContent)
                .collect(Collectors.joining("\n"));
    }

    public List<String> allProfiles(Long userId) {
        return repo.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(UserProfile::getContent).toList();
    }
}
