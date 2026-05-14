package com.example.agentplatform.model;

import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "ai_providers")
public class AiProviderEntity {
    @Id
    @Column(name = "provider_id", length = 64, nullable = false)
    private String providerId;

    @Column(name = "user_id", length = 64, nullable = false)
    private String userId;

    @Column(name = "name", length = 120, nullable = false)
    private String name;

    @Column(name = "provider_type", length = 80)
    private String providerType;

    @Column(name = "region", length = 40)
    private String region;

    @Column(name = "preset_key", length = 120)
    private String presetKey;

    @Column(name = "api_format", length = 80)
    private String apiFormat;

    @Column(name = "auth_header_name", length = 120)
    private String authHeaderName;

    @Column(name = "capabilities", length = 240)
    private String capabilities;

    @Column(name = "base_url", length = 500, nullable = false)
    private String baseUrl;

    @Column(name = "api_key_ciphertext", columnDefinition = "TEXT", nullable = false)
    private String apiKeyCiphertext;

    @Column(name = "model_name", length = 120)
    private String modelName;

    @Column(name = "default_model", length = 160)
    private String defaultModel;

    @Column(name = "chat_model", length = 160)
    private String chatModel;

    @Column(name = "image_model", length = 160)
    private String imageModel;

    @Column(name = "video_model", length = 160)
    private String videoModel;

    @Column(name = "knowledge_model", length = 160)
    private String knowledgeModel;

    @Column(name = "official_url", length = 500)
    private String officialUrl;

    @Column(name = "remark", length = 500)
    private String remark;

    @Column(name = "config_json", columnDefinition = "TEXT")
    private String configJson;

    @Column(name = "model_aliases", columnDefinition = "TEXT")
    private String modelAliases;

    @Column(name = "enable_fallback", nullable = false)
    private boolean enableFallback;

    @Column(name = "last_tested_at")
    private Instant lastTestedAt;

    @Column(name = "last_test_status", length = 40)
    private String lastTestStatus;

    @Column(name = "last_test_message", length = 1000)
    private String lastTestMessage;

    @Column(name = "last_test_error_code", length = 80)
    private String lastTestErrorCode;

    @Column(name = "last_test_http_status")
    private Integer lastTestHttpStatus;

    @Column(name = "last_test_request_id", length = 120)
    private String lastTestRequestId;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getProviderType() { return providerType; }
    public void setProviderType(String providerType) { this.providerType = providerType; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getPresetKey() { return presetKey; }
    public void setPresetKey(String presetKey) { this.presetKey = presetKey; }
    public String getApiFormat() { return apiFormat; }
    public void setApiFormat(String apiFormat) { this.apiFormat = apiFormat; }
    public String getAuthHeaderName() { return authHeaderName; }
    public void setAuthHeaderName(String authHeaderName) { this.authHeaderName = authHeaderName; }
    public String getCapabilities() { return capabilities; }
    public void setCapabilities(String capabilities) { this.capabilities = capabilities; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKeyCiphertext() { return apiKeyCiphertext; }
    public void setApiKeyCiphertext(String apiKeyCiphertext) { this.apiKeyCiphertext = apiKeyCiphertext; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getDefaultModel() { return defaultModel; }
    public void setDefaultModel(String defaultModel) { this.defaultModel = defaultModel; }
    public String getChatModel() { return chatModel; }
    public void setChatModel(String chatModel) { this.chatModel = chatModel; }
    public String getImageModel() { return imageModel; }
    public void setImageModel(String imageModel) { this.imageModel = imageModel; }
    public String getVideoModel() { return videoModel; }
    public void setVideoModel(String videoModel) { this.videoModel = videoModel; }
    public String getKnowledgeModel() { return knowledgeModel; }
    public void setKnowledgeModel(String knowledgeModel) { this.knowledgeModel = knowledgeModel; }
    public String getOfficialUrl() { return officialUrl; }
    public void setOfficialUrl(String officialUrl) { this.officialUrl = officialUrl; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }
    public String getModelAliases() { return modelAliases; }
    public void setModelAliases(String modelAliases) { this.modelAliases = modelAliases; }
    public boolean isEnableFallback() { return enableFallback; }
    public void setEnableFallback(boolean enableFallback) { this.enableFallback = enableFallback; }
    public Instant getLastTestedAt() { return lastTestedAt; }
    public void setLastTestedAt(Instant lastTestedAt) { this.lastTestedAt = lastTestedAt; }
    public String getLastTestStatus() { return lastTestStatus; }
    public void setLastTestStatus(String lastTestStatus) { this.lastTestStatus = lastTestStatus; }
    public String getLastTestMessage() { return lastTestMessage; }
    public void setLastTestMessage(String lastTestMessage) { this.lastTestMessage = lastTestMessage; }
    public String getLastTestErrorCode() { return lastTestErrorCode; }
    public void setLastTestErrorCode(String lastTestErrorCode) { this.lastTestErrorCode = lastTestErrorCode; }
    public Integer getLastTestHttpStatus() { return lastTestHttpStatus; }
    public void setLastTestHttpStatus(Integer lastTestHttpStatus) { this.lastTestHttpStatus = lastTestHttpStatus; }
    public String getLastTestRequestId() { return lastTestRequestId; }
    public void setLastTestRequestId(String lastTestRequestId) { this.lastTestRequestId = lastTestRequestId; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
