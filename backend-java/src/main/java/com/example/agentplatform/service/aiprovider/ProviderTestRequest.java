package com.example.agentplatform.service.aiprovider;

public class ProviderTestRequest {
    private final String baseUrl;
    private final String apiKey;
    private final String authHeaderName;
    private final String model;
    private final String prompt;
    private final String requestId;

    public ProviderTestRequest(String baseUrl, String apiKey, String authHeaderName,
            String model, String prompt, String requestId) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.authHeaderName = authHeaderName;
        this.model = model;
        this.prompt = prompt;
        this.requestId = requestId;
    }

    public String getBaseUrl() { return baseUrl; }
    public String getApiKey() { return apiKey; }
    public String getAuthHeaderName() { return authHeaderName; }
    public String getModel() { return model; }
    public String getPrompt() { return prompt; }
    public String getRequestId() { return requestId; }
}
