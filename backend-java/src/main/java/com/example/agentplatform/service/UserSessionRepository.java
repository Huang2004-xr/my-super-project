package com.example.agentplatform.service;

import com.example.agentplatform.model.UserSessionEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSessionRepository extends JpaRepository<UserSessionEntity, String> {
    Optional<UserSessionEntity> findByTokenHash(String tokenHash);
}
