package com.example.agentplatform.model;

import java.time.Instant;

public class ConversationMessageDto {
    public String messageId;
    public String conversationId;
    public String userId;
    public String role;
    public String content;
    public String capability;
    public String runId;
    public Instant createdAt;
}
