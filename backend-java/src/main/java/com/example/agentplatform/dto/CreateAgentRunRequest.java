package com.example.agentplatform.dto;

import java.util.LinkedHashMap;
import java.util.Map;

public class CreateAgentRunRequest {
    public String conversationId;
    public String message;
    public String imageAssetId;
    public boolean useKnowledgeBase;
    public String knowledgeBaseId;
    public String capabilityHint;
    public Map<String, Object> input = new LinkedHashMap<>();
    public Map<String, Object> providerConfig;
}
