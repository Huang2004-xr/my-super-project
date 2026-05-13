package com.example.agentplatform.service;

import com.example.agentplatform.model.AiProviderEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiProviderRepository extends JpaRepository<AiProviderEntity, String> {
    List<AiProviderEntity> findByUserIdOrderByUpdatedAtDesc(String userId);
    Optional<AiProviderEntity> findByProviderIdAndUserId(String providerId, String userId);
    List<AiProviderEntity> findByUserIdAndEnabledTrueOrderByUpdatedAtDesc(String userId);
}
