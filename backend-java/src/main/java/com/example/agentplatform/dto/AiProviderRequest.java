package com.example.agentplatform.dto;

import java.util.List;

public class AiProviderRequest {
    public String name;
    public String providerType;
    public String region;
    public String presetKey;
    public String apiFormat;
    public String authHeaderName;
    public List<String> capabilities;
    public String baseUrl;
    public String apiKey;
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
    public Boolean enableFallback;
    public Boolean enabled;
}
