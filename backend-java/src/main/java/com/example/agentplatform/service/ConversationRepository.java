package com.example.agentplatform.service;

import com.example.agentplatform.model.ConversationEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<ConversationEntity, String> {
    List<ConversationEntity> findByUserIdOrderByUpdatedAtDesc(String userId);
    Optional<ConversationEntity> findByConversationIdAndUserId(String conversationId, String userId);
}
