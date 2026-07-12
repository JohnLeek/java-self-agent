package com.agent.repository;

import com.agent.dao.AgentTrace;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AgentTraceRepository extends JpaRepository<AgentTrace, Long> {
    List<AgentTrace> findBySessionIdAndRoundOrderByStepOrderAsc(String sessionId, Integer round);
    List<AgentTrace> findBySessionIdAndStatusInOrderByStepOrderAsc(String sessionId, List<String> statuses);
}
