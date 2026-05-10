package com.example.agentplatform.model;

import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "image_assets")
public class ImageAssetEntity {
    @Id
    @Column(name = "image_id", length = 64, nullable = false)
    private String imageId;

    @Column(name = "user_id", length = 64, nullable = false)
    private String userId;

    @Column(name = "file_asset_id", length = 64, nullable = false)
    private String fileAssetId;

    @Column(name = "prompt", columnDefinition = "TEXT")
    private String prompt;

    @Column(name = "source", length = 40)
    private String source;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public String getImageId() { return imageId; }
    public void setImageId(String imageId) { this.imageId = imageId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getFileAssetId() { return fileAssetId; }
    public void setFileAssetId(String fileAssetId) { this.fileAssetId = fileAssetId; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Integer getWidth() { return width; }
    public void setWidth(Integer width) { this.width = width; }
    public Integer getHeight() { return height; }
    public void setHeight(Integer height) { this.height = height; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
