package com.example.agentplatform.config;

import com.example.agentplatform.model.UserEntity;
import com.example.agentplatform.service.AuthService;
import com.example.agentplatform.service.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {
    private final UserRepository userRepository;
    private final AuthService authService;
    private final JdbcTemplate jdbcTemplate;

    public DataInitializer(UserRepository userRepository, AuthService authService, JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.authService = authService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        UserEntity admin = createUserIfMissing("admin", "admin123", "ADMIN");
        createUserIfMissing("user", "user123", "USER");
        jdbcTemplate.update("update conversations set user_id = ? where user_id is null or user_id = ''",
                admin.getUserId());
    }

    private UserEntity createUserIfMissing(String username, String password, String role) {
        java.util.Optional<UserEntity> existing = userRepository.findByUsername(username);
        if (existing.isPresent()) {
            return existing.get();
        }
        UserEntity user = new UserEntity();
        user.setUserId(UUID.randomUUID().toString());
        user.setUsername(username);
        user.setPasswordHash(authService.hashPassword(password));
        user.setRole(role);
        user.setStatus("ACTIVE");
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        return userRepository.save(user);
    }
}
