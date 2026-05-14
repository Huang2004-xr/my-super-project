package com.example.agentplatform.dto;

import java.util.List;
import java.util.Map;

public class AiProviderPresetResponse {
    public String key;
    public String label;
    public String region;
    public String apiFormat;
    public String authHeaderName;
    public String baseUrl;
    public String defaultModel;
    public String officialUrl;
    public String remark;
    public Map<String, String> modelAliases;
    public List<String> modelOptions;

    public AiProviderPresetResponse() {
    }

    public AiProviderPresetResponse(String key, String label, String region, String apiFormat,
            String authHeaderName, String baseUrl, String defaultModel, String officialUrl,
            String remark, Map<String, String> modelAliases, List<String> modelOptions) {
        this.key = key;
        this.label = label;
        this.region = region;
        this.apiFormat = apiFormat;
        this.authHeaderName = authHeaderName;
        this.baseUrl = baseUrl;
        this.defaultModel = defaultModel;
        this.officialUrl = officialUrl;
        this.remark = remark;
        this.modelAliases = modelAliases;
        this.modelOptions = modelOptions;
    }
}
