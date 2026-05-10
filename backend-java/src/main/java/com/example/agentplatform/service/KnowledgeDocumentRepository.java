package com.example.agentplatform.service;

import com.example.agentplatform.model.KnowledgeDocumentEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocumentEntity, String> {
    List<KnowledgeDocumentEntity> findByKnowledgeBaseIdAndUserIdOrderByCreatedAtDesc(String knowledgeBaseId, String userId);
    List<KnowledgeDocumentEntity> findByKnowledgeBaseIdAndUserId(String knowledgeBaseId, String userId);
    java.util.Optional<KnowledgeDocumentEntity> findByDocumentIdAndUserId(String documentId, String userId);
    void deleteByKnowledgeBaseIdAndUserId(String knowledgeBaseId, String userId);
}
