package com.example.agentplatform.service;

import com.example.agentplatform.model.KnowledgeChunkEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunkEntity, String> {
    List<KnowledgeChunkEntity> findByKnowledgeBaseIdAndUserIdOrderByChunkIndexAsc(String knowledgeBaseId, String userId);
    void deleteByKnowledgeBaseIdAndUserId(String knowledgeBaseId, String userId);
    void deleteByDocumentIdAndUserId(String documentId, String userId);
    List<KnowledgeChunkEntity> findByDocumentIdAndUserIdOrderByChunkIndexAsc(String documentId, String userId);
}
