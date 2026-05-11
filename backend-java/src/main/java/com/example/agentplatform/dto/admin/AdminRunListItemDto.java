package com.example.agentplatform.dto.admin;

import java.time.Instant;

public class AdminRunListItemDto {
    public String runId;
    public String userId;
    public String username;
    public String capability;
    public String status;
    public boolean useKnowledgeBase;
    public String knowledgeBaseId;
    public String knowledgeBaseName;
    public String message;
    public Instant createdAt;
    public Instant updatedAt;
}