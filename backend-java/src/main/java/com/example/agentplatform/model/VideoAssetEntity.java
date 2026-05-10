package com.example.agentplatform.model;

import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "video_assets")
public class VideoAssetEntity {
    @Id
    @Column(name = "video_id", length = 64, nullable = false)
    private String videoId;

    @Column(name = "user_id", length = 64, nullable = false)
    private String userId;

    @Column(name = "file_asset_id", length = 64)
    private String fileAssetId;

    @Column(name = "prompt", columnDefinition = "TEXT")
    private String prompt;

    @Column(name = "script", columnDefinition = "TEXT")
    private String script;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "status", length = 40, nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public String getVideoId() { return videoId; }
    public void setVideoId(String videoId) { this.videoId = videoId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getFileAssetId() { return fileAssetId; }
    public void setFileAssetId(String fileAssetId) { this.fileAssetId = fileAssetId; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public String getScript() { return script; }
    public void setScript(String script) { this.script = script; }
    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
