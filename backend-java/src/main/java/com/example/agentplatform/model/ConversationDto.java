package com.example.agentplatform.model;

import java.time.Instant;

public class ConversationDto {
    public String conversationId;
    public String userId;
    public String title;
    public String firstMessage;
    public String lastRunId;
    public String memorySummary;
    public Instant memoryUpdatedAt;
    public Instant createdAt;
    public Instant updatedAt;
}
