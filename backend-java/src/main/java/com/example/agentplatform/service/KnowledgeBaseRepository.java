package com.example.agentplatform.service;

import com.example.agentplatform.model.KnowledgeBaseEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBaseEntity, String> {
    List<KnowledgeBaseEntity> findByUserIdOrderByUpdatedAtDesc(String userId);
    Optional<KnowledgeBaseEntity> findByKnowledgeBaseIdAndUserId(String knowledgeBaseId, String userId);
}
