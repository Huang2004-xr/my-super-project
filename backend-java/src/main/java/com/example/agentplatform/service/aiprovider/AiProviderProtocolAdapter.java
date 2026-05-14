package com.example.agentplatform.service.aiprovider;

public interface AiProviderProtocolAdapter {
    boolean supports(String apiFormat);

    ProviderTestResult test(ProviderTestRequest request);
}
