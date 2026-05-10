package com.example.agentplatform.service;

import com.example.agentplatform.model.AgentRunEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRunJpaRepository extends JpaRepository<AgentRunEntity, String> {
    List<AgentRunEntity> findByUserIdOrderByCreatedAtDesc(String userId);
    Optional<AgentRunEntity> findByRunIdAndUserId(String runId, String userId);
}
