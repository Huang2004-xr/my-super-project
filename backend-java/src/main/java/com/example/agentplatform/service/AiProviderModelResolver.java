package com.example.agentplatform.service;

import com.example.agentplatform.model.AiProviderEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class AiProviderModelResolver {
    private final ObjectMapper objectMapper;

    public AiProviderModelResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String resolve(AiProviderEntity entity, String capability) {
        if ("IMAGE_CREATION".equals(capability)) {
            return firstNonBlank(entity.getImageModel(), alias(entity, "imagePrompt"), alias(entity, "image"),
                    alias(entity, "main"), entity.getDefaultModel(), entity.getModelName());
        }
        if ("VIDEO_CREATION".equals(capability)) {
            return firstNonBlank(entity.getVideoModel(), alias(entity, "videoScript"), alias(entity, "video"),
                    alias(entity, "main"), entity.getDefaultModel(), entity.getModelName());
        }
        if ("KNOWLEDGE_RETRIEVAL".equals(capability)) {
            return firstNonBlank(entity.getKnowledgeModel(), alias(entity, "knowledge"),
                    alias(entity, "main"), entity.getDefaultModel(), entity.getModelName());
        }
        return firstNonBlank(entity.getChatModel(), alias(entity, "chat"), alias(entity, "main"),
                entity.getDefaultModel(), entity.getModelName());
    }

    public String alias(AiProviderEntity entity, String alias) {
        String value = jsonText(entity.getModelAliases(), alias);
        if (value != null) {
            return value;
        }
        return jsonText(entity.getConfigJson(), "modelAliases", alias);
    }

    public String jsonText(String json, String... path) {
        if (json == null || json.trim().isEmpty() || path == null || path.length == 0) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            for (String key : path) {
                node = node == null ? null : node.get(key);
            }
            if (node == null || node.isNull() || node.isMissingNode()) {
                return null;
            }
            return clean(node.asText());
        } catch (Exception ex) {
            return null;
        }
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

    private String clean(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
