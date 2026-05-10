package com.example.agentplatform.service;

import com.example.agentplatform.model.ConversationDto;
import com.example.agentplatform.model.ConversationEntity;
import com.example.agentplatform.model.ConversationMessageEntity;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationService {
    private static final String DEFAULT_TITLE = "新对话";
    private static final int MEMORY_CHAR_THRESHOLD = 3000;
    private static final int RECENT_MESSAGE_LIMIT = 8;
    private final ConversationRepository repository;
    private final ConversationMessageRepository messageRepository;
    private final AgentServiceClient agentServiceClient;

    public ConversationService(ConversationRepository repository, ConversationMessageRepository messageRepository,
            AgentServiceClient agentServiceClient) {
        this.repository = repository;
        this.messageRepository = messageRepository;
        this.agentServiceClient = agentServiceClient;
    }

    @Transactional
    public ConversationDto create(String userId) {
        ConversationEntity entity = new ConversationEntity();
        entity.setConversationId(UUID.randomUUID().toString());
        entity.setUserId(userId);
        entity.setTitle(DEFAULT_TITLE);
        return toDto(repository.save(entity));
    }

    public List<ConversationDto> findByUserId(String userId) {
        return repository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public ConversationDto get(String userId, String conversationId) {
        return toDto(findEntity(userId, conversationId));
    }

    public void ensureExists(String userId, String conversationId) {
        if (conversationId != null && !conversationId.trim().isEmpty()
                && !repository.findByConversationIdAndUserId(conversationId, userId).isPresent()) {
            throw new NoSuchElementException("Conversation not found: " + conversationId);
        }
    }

    @Transactional
    public Map<String, Object> prepareMemoryInput(String userId, String conversationId) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (conversationId == null || conversationId.trim().isEmpty()) {
            result.put("conversationMemory", "");
            result.put("recentMessages", Collections.emptyList());
            return result;
        }

        ConversationEntity entity = findEntity(userId, conversationId);
        List<ConversationMessageEntity> messages =
                messageRepository.findByConversationIdAndUserIdOrderByCreatedAtAsc(conversationId, userId);
        if (messages.isEmpty()) {
            result.put("conversationMemory", valueOrEmpty(entity.getMemorySummary()));
            result.put("recentMessages", Collections.emptyList());
            return result;
        }

        List<Map<String, String>> allMessages = messages.stream()
                .map(this::toMemoryMessage)
                .collect(Collectors.toList());
        int totalChars = allMessages.stream()
                .mapToInt(item -> item.get("content").length())
                .sum();

        if (totalChars > MEMORY_CHAR_THRESHOLD) {
            List<Map<String, String>> messagesToSummarize =
                    allMessages.subList(0, Math.max(0, allMessages.size() - RECENT_MESSAGE_LIMIT));
            if (!messagesToSummarize.isEmpty()) {
                String summary = agentServiceClient.summarizeMemory(entity.getMemorySummary(), messagesToSummarize);
                if (summary != null && !summary.trim().isEmpty()) {
                    entity.setMemorySummary(summary.trim());
                    entity.setMemoryUpdatedAt(Instant.now());
                    repository.save(entity);
                }
            }
        }

        List<ConversationMessageEntity> recentEntities =
                messageRepository.findTop8ByConversationIdAndUserIdOrderByCreatedAtDesc(conversationId, userId);
        Collections.reverse(recentEntities);
        result.put("conversationMemory", valueOrEmpty(entity.getMemorySummary()));
        result.put("recentMessages", recentEntities.stream().map(this::toMemoryMessage).collect(Collectors.toList()));
        return result;
    }

    @Transactional
    public ConversationDto attachRun(String userId, String conversationId, String message, String capability,
            String finalResult, String runId) {
        if (conversationId == null || conversationId.trim().isEmpty()) {
            return null;
        }
        ConversationEntity entity = findEntity(userId, conversationId);
        if (entity.getFirstMessage() == null || entity.getFirstMessage().trim().isEmpty()) {
            entity.setFirstMessage(message);
            entity.setTitle(summarize(message));
        }
        entity.setLastRunId(runId);
        saveMessage(userId, conversationId, "USER", message, capability, runId);
        saveMessage(userId, conversationId, "ASSISTANT", finalResult, capability, runId);
        return toDto(repository.save(entity));
    }

    public List<com.example.agentplatform.model.ConversationMessageDto> getMessages(String userId, String conversationId) {
        ensureExists(userId, conversationId);
        return messageRepository.findByConversationIdAndUserIdOrderByCreatedAtAsc(conversationId, userId).stream()
                .map(this::toMessageDto)
                .collect(Collectors.toList());
    }

    private void saveMessage(String userId, String conversationId, String role, String content, String capability,
            String runId) {
        com.example.agentplatform.model.ConversationMessageEntity message =
                new com.example.agentplatform.model.ConversationMessageEntity();
        message.setMessageId(UUID.randomUUID().toString());
        message.setConversationId(conversationId);
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content == null ? "" : content);
        message.setCapability(capability);
        message.setRunId(runId);
        message.setCreatedAt(java.time.Instant.now());
        messageRepository.save(message);
    }

    private ConversationEntity findEntity(String userId, String conversationId) {
        if (conversationId == null || conversationId.trim().isEmpty()) {
            throw new IllegalArgumentException("conversationId is required");
        }
        return repository.findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new NoSuchElementException("Conversation not found: " + conversationId));
    }

    private String summarize(String message) {
        String text = message == null ? "" : message.replaceAll("\\s+", " ").trim();
        if (text.isEmpty()) {
            return DEFAULT_TITLE;
        }
        if (text.length() <= 18) {
            return text;
        }
        return text.substring(0, 18) + "...";
    }

    private ConversationDto toDto(ConversationEntity entity) {
        ConversationDto dto = new ConversationDto();
        dto.conversationId = entity.getConversationId();
        dto.userId = entity.getUserId();
        dto.title = entity.getTitle();
        dto.firstMessage = entity.getFirstMessage();
        dto.lastRunId = entity.getLastRunId();
        dto.memorySummary = entity.getMemorySummary();
        dto.memoryUpdatedAt = entity.getMemoryUpdatedAt();
        dto.createdAt = entity.getCreatedAt();
        dto.updatedAt = entity.getUpdatedAt();
        return dto;
    }

    private Map<String, String> toMemoryMessage(ConversationMessageEntity entity) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("role", entity.getRole());
        item.put("content", entity.getContent() == null ? "" : entity.getContent());
        return item;
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private com.example.agentplatform.model.ConversationMessageDto toMessageDto(
            com.example.agentplatform.model.ConversationMessageEntity entity) {
        com.example.agentplatform.model.ConversationMessageDto dto =
                new com.example.agentplatform.model.ConversationMessageDto();
        dto.messageId = entity.getMessageId();
        dto.conversationId = entity.getConversationId();
        dto.userId = entity.getUserId();
        dto.role = entity.getRole();
        dto.content = entity.getContent();
        dto.capability = entity.getCapability();
        dto.runId = entity.getRunId();
        dto.createdAt = entity.getCreatedAt();
        return dto;
    }
}
