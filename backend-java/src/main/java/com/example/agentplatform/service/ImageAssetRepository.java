package com.example.agentplatform.service;

import com.example.agentplatform.model.ImageAssetEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImageAssetRepository extends JpaRepository<ImageAssetEntity, String> {
    List<ImageAssetEntity> findByUserIdOrderByCreatedAtDesc(String userId);
}
