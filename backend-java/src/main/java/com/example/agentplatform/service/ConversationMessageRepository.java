package com.example.agentplatform.service;

import com.example.agentplatform.model.ConversationMessageEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessageEntity, String> {
    List<ConversationMessageEntity> findByConversationIdAndUserIdOrderByCreatedAtAsc(String conversationId, String userId);
    List<ConversationMessageEntity> findTop8ByConversationIdAndUserIdOrderByCreatedAtDesc(String conversationId, String userId);
}
