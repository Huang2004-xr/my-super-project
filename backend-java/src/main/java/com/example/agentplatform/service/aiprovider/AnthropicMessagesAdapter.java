package com.example.agentplatform.service.aiprovider;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class AnthropicMessagesAdapter extends ProviderAdapterSupport implements AiProviderProtocolAdapter {
    private final RestTemplate restTemplate;

    public AnthropicMessagesAdapter(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public boolean supports(String apiFormat) {
        return "anthropic_messages".equals(apiFormat);
    }

    @Override
    public ProviderTestResult test(ProviderTestRequest request) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            applyAuthHeader(headers, request);
            headers.set("anthropic-version", "2023-06-01");
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", request.getModel());
            body.put("system", "You are a connection test.");
            body.put("messages", Collections.singletonList(message("user", defaulted(request.getPrompt(), "Reply with ok."))));
            body.put("max_tokens", 8);
            restTemplate.postForEntity(endpoint(request.getBaseUrl(), "/v1", "/messages"),
                    new HttpEntity<>(body, headers), String.class);
            return ProviderTestResult.ok("connection test succeeded", request.getRequestId());
        } catch (HttpStatusCodeException ex) {
            return httpError(ex);
        } catch (RestClientException ex) {
            return clientError(ex);
        }
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("role", role);
        item.put("content", content);
        return item;
    }
}
