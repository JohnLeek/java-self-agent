package com.agent.repository;

import com.agent.dao.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SessionRepository extends JpaRepository<Session, String> {
    List<Session> findByUserIdOrderByUpdatedAtDesc(Long userId);
}
