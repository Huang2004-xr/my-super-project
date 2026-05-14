package com.example.agentplatform.dto;

import java.time.Instant;
import java.util.List;

public class AiProviderResponse {
    public String providerId;
    public String userId;
    public String name;
    public String providerType;
    public String region;
    public String presetKey;
    public String apiFormat;
    public String authHeaderName;
    public List<String> capabilities;
    public String baseUrl;
    public String modelName;
    public String defaultModel;
    public String chatModel;
    public String imageModel;
    public String videoModel;
    public String knowledgeModel;
    public String officialUrl;
    public String remark;
    public String configJson;
    public String modelAliases;
    public boolean enableFallback;
    public boolean enabled;
    public boolean apiKeySet;
    public Instant lastTestedAt;
    public String lastTestStatus;
    public String lastTestMessage;
    public String lastTestErrorCode;
    public Integer lastTestHttpStatus;
    public String lastTestRequestId;
    public Instant createdAt;
    public Instant updatedAt;
}
