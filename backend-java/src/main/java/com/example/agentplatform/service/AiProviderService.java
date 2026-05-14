package com.example.agentplatform.service;

import com.example.agentplatform.dto.AiProviderRequest;
import com.example.agentplatform.dto.AiProviderResponse;
import com.example.agentplatform.dto.AiProviderTestResponse;
import com.example.agentplatform.model.AiProviderEntity;
import com.example.agentplatform.service.aiprovider.AiProviderProtocolAdapter;
import com.example.agentplatform.service.aiprovider.ProviderErrorCode;
import com.example.agentplatform.service.aiprovider.ProviderTestRequest;
import com.example.agentplatform.service.aiprovider.ProviderTestResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AiProviderService {
    private static final Set<String> SUPPORTED_CAPABILITIES = new LinkedHashSet<>(
            Arrays.asList("ALL", "TEXT_CHAT", "IMAGE_CREATION", "VIDEO_CREATION", "KNOWLEDGE_RETRIEVAL"));
    private static final Set<String> SUPPORTED_API_FORMATS = new LinkedHashSet<>(
            Arrays.asList("openai_chat_completions", "openai_responses", "anthropic_messages"));

    private final AiProviderRepository repository;
    private final CryptoService cryptoService;
    private final List<AiProviderProtocolAdapter> protocolAdapters;
    private final AiProviderModelResolver modelResolver;
    private final ObjectMapper objectMapper;

    public AiProviderService(
            AiProviderRepository repository,
            CryptoService cryptoService,
            List<AiProviderProtocolAdapter> protocolAdapters,
            AiProviderModelResolver modelResolver,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.cryptoService = cryptoService;
        this.protocolAdapters = protocolAdapters;
        this.modelResolver = modelResolver;
        this.objectMapper = objectMapper;
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
        entity.setLastTestErrorCode(result.errorCode);
        entity.setLastTestHttpStatus(result.httpStatus);
        entity.setLastTestRequestId(result.providerRequestId);
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
                fallback.put("model", "qwen3:4b");
                fallback.put("lastTestStatus", null);
                fallback.put("fallbackEnabled", false);
                result.put(capability, fallback);
            } else {
                summary.remove("apiKey");
                summary.remove("fallbackProviders");
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
        Map<String, Object> config = toInternalConfig(selected, capability);
        if (selected.isEnableFallback()) {
            List<Map<String, Object>> fallbacks = providers.stream()
                    .filter(item -> !item.getProviderId().equals(selected.getProviderId()))
                    .filter(item -> capabilities(item).contains(capability) || capabilities(item).contains("ALL"))
                    .map(item -> toInternalConfig(item, capability))
                    .collect(Collectors.toList());
            config.put("fallbackProviders", fallbacks);
        }
        return config;
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
        entity.setRegion(defaulted(request.region, "CUSTOM"));
        entity.setPresetKey(clean(request.presetKey));
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
        entity.setConfigJson(validateJson(request.configJson, "configJson"));
        entity.setModelAliases(validateJson(request.modelAliases, "modelAliases"));
        if (request.enableFallback != null) {
            entity.setEnableFallback(request.enableFallback);
        }
        if (request.enabled != null) {
            entity.setEnabled(request.enabled);
        }
    }

    private AiProviderTestResponse testEntity(AiProviderEntity entity) {
        Instant testedAt = Instant.now();
        String model = firstNonBlank(modelResolver.jsonText(entity.getConfigJson(), "test", "model"), modelForCapability(entity, "TEXT_CHAT"));
        if (model == null || model.trim().isEmpty()) {
            return new AiProviderTestResponse(false, "FAILED", "model is required for connection test",
                    ProviderErrorCode.MODEL_NOT_FOUND.name(), null, null, testedAt);
        }
        String apiFormat = defaulted(entity.getApiFormat(), "openai_chat_completions");
        Optional<AiProviderProtocolAdapter> adapter = protocolAdapters.stream()
                .filter(item -> item.supports(apiFormat))
                .findFirst();
        if (adapter.isEmpty()) {
            return new AiProviderTestResponse(false, "FAILED", "unsupported apiFormat: " + apiFormat,
                    ProviderErrorCode.PROTOCOL_MISMATCH.name(), null, null, testedAt);
        }
        String requestId = UUID.randomUUID().toString();
        ProviderTestResult result = adapter.get().test(new ProviderTestRequest(
                entity.getBaseUrl(),
                cryptoService.decrypt(entity.getApiKeyCiphertext()),
                defaultAuthHeader(entity.getAuthHeaderName(), entity.getApiFormat()),
                model,
                modelResolver.jsonText(entity.getConfigJson(), "test", "prompt"),
                requestId));
        if (result.isOk()) {
            return new AiProviderTestResponse(true, "SUCCESS", "connection test succeeded",
                    null, result.getHttpStatus(), firstNonBlank(result.getProviderRequestId(), requestId), testedAt);
        }
        String message = improveProviderMessage(entity, model, result.getMessage());
        return new AiProviderTestResponse(false, "FAILED", message,
                result.getErrorCode() == null ? ProviderErrorCode.UNKNOWN.name() : result.getErrorCode().name(),
                result.getHttpStatus(), firstNonBlank(result.getProviderRequestId(), requestId), testedAt);
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
        config.put("requestId", UUID.randomUUID().toString());
        config.put("capability", capability);
        config.put("configJson", entity.getConfigJson());
        config.put("region", defaulted(entity.getRegion(), "CUSTOM"));
        config.put("presetKey", entity.getPresetKey());
        config.put("modelAliases", entity.getModelAliases());
        config.put("enableFallback", entity.isEnableFallback());
        config.put("fallbackEnabled", entity.isEnableFallback());
        config.put("lastTestStatus", entity.getLastTestStatus());
        config.put("lastTestedAt", entity.getLastTestedAt());
        config.put("lastTestMessage", entity.getLastTestMessage());
        config.put("lastTestErrorCode", entity.getLastTestErrorCode());
        return config;
    }

    private AiProviderResponse toResponse(AiProviderEntity entity) {
        AiProviderResponse dto = new AiProviderResponse();
        dto.providerId = entity.getProviderId();
        dto.userId = entity.getUserId();
        dto.name = entity.getName();
        dto.providerType = entity.getProviderType();
        dto.region = defaulted(entity.getRegion(), "CUSTOM");
        dto.presetKey = entity.getPresetKey();
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
        dto.modelAliases = entity.getModelAliases();
        dto.enableFallback = entity.isEnableFallback();
        dto.enabled = entity.isEnabled();
        dto.apiKeySet = entity.getApiKeyCiphertext() != null && !entity.getApiKeyCiphertext().isEmpty();
        dto.lastTestedAt = entity.getLastTestedAt();
        dto.lastTestStatus = entity.getLastTestStatus();
        dto.lastTestMessage = entity.getLastTestMessage();
        dto.lastTestErrorCode = entity.getLastTestErrorCode();
        dto.lastTestHttpStatus = entity.getLastTestHttpStatus();
        dto.lastTestRequestId = entity.getLastTestRequestId();
        dto.createdAt = entity.getCreatedAt();
        dto.updatedAt = entity.getUpdatedAt();
        return dto;
    }

    private String modelForCapability(AiProviderEntity entity, String capability) {
        return modelResolver.resolve(entity, capability);
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

    private String validateJson(String value, String field) {
        String cleaned = clean(value);
        if (cleaned == null) {
            return null;
        }
        try {
            objectMapper.readTree(cleaned);
            return cleaned;
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException(field + " must be valid JSON");
        }
    }

    private String improveProviderMessage(AiProviderEntity entity, String model, String message) {
        if ("mimo-token-plan-cn".equals(entity.getPresetKey())
                && model != null
                && !"mimo-v2.5-pro".equals(model)
                && "mimo-v2.5-pro".equalsIgnoreCase(model)) {
            return message + "; MiMo Token Plan CN 通常使用小写模型 id: mimo-v2.5-pro";
        }
        return message;
    }
}
