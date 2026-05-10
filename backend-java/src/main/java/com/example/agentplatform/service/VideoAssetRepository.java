package com.example.agentplatform.service;

import com.example.agentplatform.model.VideoAssetEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VideoAssetRepository extends JpaRepository<VideoAssetEntity, String> {
    List<VideoAssetEntity> findByUserIdOrderByCreatedAtDesc(String userId);
}
