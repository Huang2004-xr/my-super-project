package com.example.agentplatform.service;

import com.example.agentplatform.model.FileAssetEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileAssetRepository extends JpaRepository<FileAssetEntity, String> {
    List<FileAssetEntity> findByUserIdAndAssetTypeOrderByCreatedAtDesc(String userId, String assetType);
    List<FileAssetEntity> findByUserIdAndAssetTypeAndFileAssetIdIn(String userId, String assetType, List<String> fileAssetIds);
    Optional<FileAssetEntity> findByFileAssetIdAndUserId(String fileAssetId, String userId);
}
