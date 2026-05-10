package com.example.agentplatform.service;

import com.example.agentplatform.dto.AuthResponse;
import com.example.agentplatform.dto.LoginRequest;
import com.example.agentplatform.model.UserDto;
import com.example.agentplatform.model.UserEntity;
import com.example.agentplatform.model.UserSessionEntity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.NoSuchElementException;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final UserSessionRepository sessionRepository;

    public AuthService(UserRepository userRepository, UserSessionRepository sessionRepository) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        if (request.username == null || request.username.trim().isEmpty()) {
            throw new IllegalArgumentException("username is required");
        }
        if (request.password == null || request.password.isEmpty()) {
            throw new IllegalArgumentException("password is required");
        }
        UserEntity user = userRepository.findByUsername(request.username.trim())
                .orElseThrow(() -> new IllegalArgumentException("invalid username or password"));
        if (!"ACTIVE".equals(user.getStatus()) || !hash(request.password).equals(user.getPasswordHash())) {
            throw new IllegalArgumentException("invalid username or password");
        }
        String token = UUID.randomUUID().toString() + UUID.randomUUID().toString();
        UserSessionEntity session = new UserSessionEntity();
        session.setSessionId(UUID.randomUUID().toString());
        session.setUserId(user.getUserId());
        session.setTokenHash(hash(token));
        session.setCreatedAt(Instant.now());
        session.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        sessionRepository.save(session);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
        return new AuthResponse(token, toDto(user));
    }

    @Transactional
    public void logout(HttpServletRequest request) {
        String token = extractToken(request);
        if (token == null) {
            return;
        }
        sessionRepository.findByTokenHash(hash(token)).ifPresent(session -> {
            session.setRevokedAt(Instant.now());
            sessionRepository.save(session);
        });
    }

    public UserEntity requireUser(HttpServletRequest request) {
        String token = extractToken(request);
        if (token == null) {
            throw new IllegalArgumentException("login required");
        }
        UserSessionEntity session = sessionRepository.findByTokenHash(hash(token))
                .orElseThrow(() -> new IllegalArgumentException("invalid session"));
        if (session.getRevokedAt() != null || session.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("session expired");
        }
        return userRepository.findById(session.getUserId())
                .orElseThrow(() -> new NoSuchElementException("User not found: " + session.getUserId()));
    }

    public UserEntity requireAdmin(HttpServletRequest request) {
        UserEntity user = requireUser(request);
        if (!"ADMIN".equals(user.getRole())) {
            throw new IllegalArgumentException("admin role required");
        }
        return user;
    }

    public UserDto me(HttpServletRequest request) {
        return toDto(requireUser(request));
    }

    public UserDto toDto(UserEntity user) {
        UserDto dto = new UserDto();
        dto.userId = user.getUserId();
        dto.username = user.getUsername();
        dto.email = user.getEmail();
        dto.phone = user.getPhone();
        dto.role = user.getRole();
        dto.status = user.getStatus();
        dto.createdAt = user.getCreatedAt();
        dto.updatedAt = user.getUpdatedAt();
        dto.lastLoginAt = user.getLastLoginAt();
        return dto;
    }

    public String hashPassword(String password) {
        return hash(password);
    }

    private String extractToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring("Bearer ".length()).trim();
        }
        String token = request.getHeader("X-Auth-Token");
        return token == null || token.trim().isEmpty() ? null : token.trim();
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to hash value", ex);
        }
    }
}
