package com.agent.repository;

import com.agent.dao.ImportedSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ImportedSkillRepository extends JpaRepository<ImportedSkill, Long> {
    List<ImportedSkill> findByUserIdOrderByPriorityDesc(Long userId);
    List<ImportedSkill> findByUserIdAndEnabledOrderByPriorityDesc(Long userId, Boolean enabled);
    List<ImportedSkill> findByUserIdAndTypeAndEnabledOrderByPriorityDesc(Long userId, String type, Boolean enabled);
}
