package com.example.agentplatform.service;

import com.example.agentplatform.dto.AiProviderRequest;
import com.example.agentplatform.dto.AiProviderResponse;
import com.example.agentplatform.dto.AiProviderTestResponse;
import com.example.agentplatform.model.AiProviderEntity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class AiProviderService {
    private static final Set<String> SUPPORTED_CAPABILITIES = new LinkedHashSet<>(
            Arrays.asList("ALL", "TEXT_CHAT", "IMAGE_CREATION", "VIDEO_CREATION", "KNOWLEDGE_RETRIEVAL"));
    private static final Set<String> SUPPORTED_API_FORMATS = new LinkedHashSet<>(
            Arrays.asList("openai_chat_completions", "openai_responses", "anthropic_messages"));

    private final AiProviderRepository repository;
    private final CryptoService cryptoService;
    private final RestTemplate restTemplate;

    public AiProviderService(AiProviderRepository repository, CryptoService cryptoService, RestTemplate restTemplate) {
        this.repository = repository;
        this.cryptoService = cryptoService;
        this.restTemplate = restTemplate;
    }

    public List<AiProviderResponse> list(String userId) {
        return repository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public AiProviderResponse create(String userId, AiProviderRequest request) {
        AiProviderEntity entity = new AiProviderEntity();
        Instant now = Instant.now();
        entity.setProviderId(UUID.randomUUID().toString());
        entity.setUserId(userId);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setEnabled(request.enabled == null || request.enabled);
        applyRequest(entity, request, true);
        return toResponse(repository.save(entity));
    }

    public AiProviderResponse update(String userId, String providerId, AiProviderRequest request) {
        AiProviderEntity entity = findOwned(userId, providerId);
        applyRequest(entity, request, false);
        entity.setUpdatedAt(Instant.now());
        return toResponse(repository.save(entity));
    }

    public void delete(String userId, String providerId) {
        repository.delete(findOwned(userId, providerId));
    }

    public AiProviderTestResponse test(String userId, String providerId) {
        AiProviderEntity entity = findOwned(userId, providerId);
        AiProviderTestResponse result = testEntity(entity);
        entity.setLastTestedAt(result.testedAt);
        entity.setLastTestStatus(result.status);
        entity.setLastTestMessage(result.message);
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);
        return result;
    }

    public Map<String, Object> effective(String userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String capability : Arrays.asList("TEXT_CHAT", "IMAGE_CREATION", "VIDEO_CREATION", "KNOWLEDGE_RETRIEVAL")) {
            Map<String, Object> summary = resolveProvider(userId, capability);
            if (summary == null) {
                Map<String, Object> fallback = new LinkedHashMap<>();
                fallback.put("providerType", "ollama");
                fallback.put("name", "Local Ollama");
                fallback.put("capability", capability);
                result.put(capability, fallback);
            } else {
                summary.remove("apiKey");
                result.put(capability, summary);
            }
        }
        return result;
    }

    public Map<String, Object> resolveProvider(String userId, String capability) {
        List<AiProviderEntity> providers = repository.findByUserIdAndEnabledTrueOrderByUpdatedAtDesc(userId);
        AiProviderEntity exact = providers.stream()
                .filter(item -> capabilities(item).contains(capability))
                .findFirst()
                .orElse(null);
        AiProviderEntity selected = exact != null ? exact : providers.stream()
                .filter(item -> capabilities(item).contains("ALL"))
                .findFirst()
                .orElse(null);
        if (selected == null) {
            return null;
        }
        return toInternalConfig(selected, capability);
    }

    private AiProviderEntity findOwned(String userId, String providerId) {
        return repository.findByProviderIdAndUserId(providerId, userId)
                .orElseThrow(() -> new NoSuchElementException("AI provider not found: " + providerId));
    }

    private void applyRequest(AiProviderEntity entity, AiProviderRequest request, boolean requireApiKey) {
        if (request == null) {
            throw new IllegalArgumentException("provider request is required");
        }
        entity.setName(required(request.name, "name"));
        entity.setProviderType(defaulted(request.providerType, "external"));
        String apiFormat = defaulted(request.apiFormat, "openai_chat_completions");
        if (!SUPPORTED_API_FORMATS.contains(apiFormat)) {
            throw new IllegalArgumentException("unsupported apiFormat: " + apiFormat);
        }
        entity.setApiFormat(apiFormat);
        entity.setAuthHeaderName(defaultAuthHeader(request.authHeaderName, apiFormat));
        entity.setCapabilities(serializeCapabilities(request.capabilities));
        entity.setBaseUrl(stripTrailingSlash(required(request.baseUrl, "baseUrl")));
        if (request.apiKey != null && !request.apiKey.trim().isEmpty()) {
            entity.setApiKeyCiphertext(cryptoService.encrypt(request.apiKey.trim()));
        } else if (requireApiKey) {
            throw new IllegalArgumentException("apiKey is required");
        }
        entity.setModelName(clean(request.modelName));
        entity.setDefaultModel(clean(request.defaultModel));
        entity.setChatModel(clean(request.chatModel));
        entity.setImageModel(clean(request.imageModel));
        entity.setVideoModel(clean(request.videoModel));
        entity.setKnowledgeModel(clean(request.knowledgeModel));
        entity.setOfficialUrl(clean(request.officialUrl));
        entity.setRemark(clean(request.remark));
        entity.setConfigJson(clean(request.configJson));
        if (request.enabled != null) {
            entity.setEnabled(request.enabled);
        }
    }

    private AiProviderTestResponse testEntity(AiProviderEntity entity) {
        Instant testedAt = Instant.now();
        String model = firstNonBlank(extractJsonString(entity.getConfigJson(), "model"), modelForCapability(entity, "TEXT_CHAT"));
        if (model == null || model.trim().isEmpty()) {
            return new AiProviderTestResponse(false, "FAILED", "model is required for connection test", testedAt);
        }
        String apiFormat = defaulted(entity.getApiFormat(), "openai_chat_completions");
        if (!"openai_chat_completions".equals(apiFormat)) {
            return new AiProviderTestResponse(false, "FAILED", "test is only implemented for OpenAI Chat Completions in this version", testedAt);
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            applyAuthHeader(headers, entity);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", Arrays.asList(
                    message("system", "You are a connection test."),
                    message("user", "Reply with ok.")));
            body.put("max_tokens", 8);
            restTemplate.postForEntity(endpoint(entity.getBaseUrl(), "/chat/completions"),
                    new HttpEntity<>(body, headers), String.class);
            return new AiProviderTestResponse(true, "SUCCESS", "connection test succeeded", testedAt);
        } catch (HttpStatusCodeException ex) {
            return new AiProviderTestResponse(false, "FAILED", readableProviderError(ex), testedAt);
        } catch (RestClientException ex) {
            return new AiProviderTestResponse(false, "FAILED", "connection test failed: " + ex.getMessage(), testedAt);
        }
    }

    private String readableProviderError(HttpStatusCodeException ex) {
        String body = ex.getResponseBodyAsString();
        String normalized = body == null ? "" : body
                .replace("<EOL>", " ")
                .replace("\\n", " ")
                .replaceAll("\\s+", " ")
                .trim();
        String message = extractJsonString(normalized, "message");
        String param = extractJsonString(normalized, "param");
        if (message != null && message.toLowerCase().contains("not supported model")) {
            return "model is not supported by this endpoint"
                    + (param == null ? "" : ": " + param)
                    + ". Please use a model name listed by the provider.";
        }
        if (message != null) {
            return "connection test failed: HTTP " + ex.getRawStatusCode() + ". " + message
                    + (param == null ? "" : " (" + param + ")");
        }
        return "connection test failed: HTTP " + ex.getRawStatusCode()
                + (normalized.isEmpty() ? "" : ". " + normalized);
    }

    private String extractJsonString(String text, String key) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"")
                .matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private Map<String, Object> toInternalConfig(AiProviderEntity entity, String capability) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("providerId", entity.getProviderId());
        config.put("name", entity.getName());
        config.put("providerType", defaulted(entity.getProviderType(), "external"));
        config.put("apiFormat", defaulted(entity.getApiFormat(), "openai_chat_completions"));
        config.put("authHeaderName", defaultAuthHeader(entity.getAuthHeaderName(), entity.getApiFormat()));
        config.put("baseUrl", entity.getBaseUrl());
        config.put("apiKey", cryptoService.decrypt(entity.getApiKeyCiphertext()));
        config.put("model", modelForCapability(entity, capability));
        config.put("capability", capability);
        config.put("configJson", entity.getConfigJson());
        return config;
    }

    private AiProviderResponse toResponse(AiProviderEntity entity) {
        AiProviderResponse dto = new AiProviderResponse();
        dto.providerId = entity.getProviderId();
        dto.userId = entity.getUserId();
        dto.name = entity.getName();
        dto.providerType = entity.getProviderType();
        dto.apiFormat = defaulted(entity.getApiFormat(), "openai_chat_completions");
        dto.authHeaderName = defaultAuthHeader(entity.getAuthHeaderName(), dto.apiFormat);
        dto.capabilities = capabilities(entity);
        dto.baseUrl = entity.getBaseUrl();
        dto.modelName = entity.getModelName();
        dto.defaultModel = firstNonBlank(entity.getDefaultModel(), entity.getModelName());
        dto.chatModel = entity.getChatModel();
        dto.imageModel = entity.getImageModel();
        dto.videoModel = entity.getVideoModel();
        dto.knowledgeModel = entity.getKnowledgeModel();
        dto.officialUrl = entity.getOfficialUrl();
        dto.remark = entity.getRemark();
        dto.configJson = entity.getConfigJson();
        dto.enabled = entity.isEnabled();
        dto.apiKeySet = entity.getApiKeyCiphertext() != null && !entity.getApiKeyCiphertext().isEmpty();
        dto.lastTestedAt = entity.getLastTestedAt();
        dto.lastTestStatus = entity.getLastTestStatus();
        dto.lastTestMessage = entity.getLastTestMessage();
        dto.createdAt = entity.getCreatedAt();
        dto.updatedAt = entity.getUpdatedAt();
        return dto;
    }

    private String modelForCapability(AiProviderEntity entity, String capability) {
        if ("IMAGE_CREATION".equals(capability)) {
            return firstNonBlank(entity.getImageModel(), entity.getDefaultModel(), entity.getModelName());
        }
        if ("VIDEO_CREATION".equals(capability)) {
            return firstNonBlank(entity.getVideoModel(), entity.getDefaultModel(), entity.getModelName());
        }
        if ("KNOWLEDGE_RETRIEVAL".equals(capability)) {
            return firstNonBlank(entity.getKnowledgeModel(), entity.getDefaultModel(), entity.getModelName());
        }
        return firstNonBlank(entity.getChatModel(), entity.getDefaultModel(), entity.getModelName());
    }

    private List<String> capabilities(AiProviderEntity entity) {
        String raw = entity.getCapabilities();
        if (raw == null || raw.trim().isEmpty()) {
            return Arrays.asList("ALL");
        }
        List<String> values = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .filter(SUPPORTED_CAPABILITIES::contains)
                .collect(Collectors.toList());
        return values.isEmpty() ? Arrays.asList("ALL") : values;
    }

    private String serializeCapabilities(List<String> capabilities) {
        List<String> values = capabilities == null ? new ArrayList<>() : capabilities.stream()
                .map(this::clean)
                .filter(item -> item != null && SUPPORTED_CAPABILITIES.contains(item))
                .distinct()
                .collect(Collectors.toList());
        if (values.isEmpty()) {
            values.add("ALL");
        }
        if (values.contains("ALL")) {
            return "ALL";
        }
        return String.join(",", values);
    }

    private void applyAuthHeader(HttpHeaders headers, AiProviderEntity entity) {
        String key = cryptoService.decrypt(entity.getApiKeyCiphertext());
        String headerName = defaultAuthHeader(entity.getAuthHeaderName(), entity.getApiFormat());
        if ("Authorization".equalsIgnoreCase(headerName)) {
            headers.set(HttpHeaders.AUTHORIZATION, key.toLowerCase().startsWith("bearer ") ? key : "Bearer " + key);
            return;
        }
        headers.set(headerName, key);
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("role", role);
        item.put("content", content);
        return item;
    }

    private String endpoint(String baseUrl, String suffix) {
        String normalized = stripTrailingSlash(baseUrl);
        if (normalized.endsWith("/chat/completions")) {
            return normalized;
        }
        if (normalized.endsWith("/v1")) {
            return normalized + suffix;
        }
        return normalized + "/v1" + suffix;
    }

    private String defaultAuthHeader(String value, String apiFormat) {
        String cleaned = clean(value);
        if (cleaned != null) {
            return cleaned;
        }
        return "anthropic_messages".equals(apiFormat) ? "x-api-key" : "Authorization";
    }

    private String required(String value, String field) {
        String cleaned = clean(value);
        if (cleaned == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return cleaned;
    }

    private String clean(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private String defaulted(String value, String fallback) {
        String cleaned = clean(value);
        return cleaned == null ? fallback : cleaned;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String cleaned = clean(value);
            if (cleaned != null) {
                return cleaned;
            }
        }
        return null;
    }

    private String stripTrailingSlash(String value) {
        String cleaned = required(value, "baseUrl");
        while (cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return cleaned;
    }
}
