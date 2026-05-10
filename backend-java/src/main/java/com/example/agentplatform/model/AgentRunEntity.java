package com.example.agentplatform.model;

import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "agent_runs")
public class AgentRunEntity {
    @Id
    @Column(name = "run_id", length = 64, nullable = false)
    private String runId;

    @Column(name = "user_id", length = 64, nullable = false)
    private String userId;

    @Column(name = "conversation_id", length = 64)
    private String conversationId;

    @Column(name = "capability", length = 40, nullable = false)
    private String capability;

    @Column(name = "message", columnDefinition = "TEXT", nullable = false)
    private String message;

    @Column(name = "image_asset_id", length = 64)
    private String imageAssetId;

    @Column(name = "use_knowledge_base", nullable = false)
    private boolean useKnowledgeBase;

    @Column(name = "knowledge_base_id", length = 64)
    private String knowledgeBaseId;

    @Column(name = "status", length = 40, nullable = false)
    private String status;

    @Column(name = "route_reason", columnDefinition = "TEXT")
    private String routeReason;

    @Column(name = "final_result", columnDefinition = "TEXT")
    private String finalResult;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public String getCapability() { return capability; }
    public void setCapability(String capability) { this.capability = capability; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getImageAssetId() { return imageAssetId; }
    public void setImageAssetId(String imageAssetId) { this.imageAssetId = imageAssetId; }
    public boolean isUseKnowledgeBase() { return useKnowledgeBase; }
    public void setUseKnowledgeBase(boolean useKnowledgeBase) { this.useKnowledgeBase = useKnowledgeBase; }
    public String getKnowledgeBaseId() { return knowledgeBaseId; }
    public void setKnowledgeBaseId(String knowledgeBaseId) { this.knowledgeBaseId = knowledgeBaseId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRouteReason() { return routeReason; }
    public void setRouteReason(String routeReason) { this.routeReason = routeReason; }
    public String getFinalResult() { return finalResult; }
    public void setFinalResult(String finalResult) { this.finalResult = finalResult; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
