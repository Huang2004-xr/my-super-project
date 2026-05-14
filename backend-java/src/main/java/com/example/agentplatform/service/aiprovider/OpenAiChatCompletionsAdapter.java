package com.example.agentplatform.service.aiprovider;

import java.util.Arrays;
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
public class OpenAiChatCompletionsAdapter extends ProviderAdapterSupport implements AiProviderProtocolAdapter {
    private final RestTemplate restTemplate;

    public OpenAiChatCompletionsAdapter(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public boolean supports(String apiFormat) {
        return "openai_chat_completions".equals(apiFormat);
    }

    @Override
    public ProviderTestResult test(ProviderTestRequest request) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            applyAuthHeader(headers, request);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", request.getModel());
            body.put("messages", Arrays.asList(
                    message("system", "You are a connection test."),
                    message("user", defaulted(request.getPrompt(), "Reply with ok."))));
            body.put("max_tokens", 8);
            body.put("stream", false);
            restTemplate.postForEntity(endpoint(request.getBaseUrl(), "/v1", "/chat/completions"),
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
