package com.example.agentplatform.service;

import com.example.agentplatform.dto.admin.AdminDashboardResponse;
import com.example.agentplatform.dto.admin.AdminCreateUserRequest;
import com.example.agentplatform.dto.admin.AdminPageResponse;
import com.example.agentplatform.dto.admin.AdminRunListItemDto;
import com.example.agentplatform.dto.admin.AdminSummaryDto;
import com.example.agentplatform.dto.admin.AdminUpdateUserRequest;
import com.example.agentplatform.model.AgentRun;
import com.example.agentplatform.model.KnowledgeBaseEntity;
import com.example.agentplatform.model.KnowledgeDocumentEntity;
import com.example.agentplatform.model.TraceEvent;
import com.example.agentplatform.model.UserDto;
import com.example.agentplatform.model.UserEntity;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class AdminQueryService {
    private final UserRepository userRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final ImageAssetRepository imageAssetRepository;
    private final VideoAssetRepository videoAssetRepository;
    private final AgentRunRepository agentRunRepository;
    private final AgentRunService agentRunService;
    private final AgentServiceClient agentServiceClient;
    private final AuthService authService;

    public AdminQueryService(
            UserRepository userRepository,
            KnowledgeBaseRepository knowledgeBaseRepository,
            KnowledgeDocumentRepository knowledgeDocumentRepository,
            ImageAssetRepository imageAssetRepository,
            VideoAssetRepository videoAssetRepository,
            AgentRunRepository agentRunRepository,
            AgentRunService agentRunService,
            AgentServiceClient agentServiceClient,
            AuthService authService) {
        this.userRepository = userRepository;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.imageAssetRepository = imageAssetRepository;
        this.videoAssetRepository = videoAssetRepository;
        this.agentRunRepository = agentRunRepository;
        this.agentRunService = agentRunService;
        this.agentServiceClient = agentServiceClient;
        this.authService = authService;
    }

    public AdminDashboardResponse getDashboard() {
        List<UserEntity> users = userRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        List<AgentRun> runs = agentRunRepository.findAllGlobal();
        Map<String, String> usernameByUserId = users.stream()
                .collect(Collectors.toMap(UserEntity::getUserId, UserEntity::getUsername, (left, right) -> left, LinkedHashMap::new));
        Map<String, String> knowledgeBaseNameById = knowledgeBaseRepository.findAll().stream()
                .collect(Collectors.toMap(KnowledgeBaseEntity::getKnowledgeBaseId, KnowledgeBaseEntity::getName, (left, right) -> left, LinkedHashMap::new));

        AdminSummaryDto summary = new AdminSummaryDto();
        summary.userCount = userRepository.count();
        summary.adminCount = users.stream().filter(user -> "ADMIN".equals(user.getRole())).count();
        summary.knowledgeBaseCount = knowledgeBaseRepository.count();
        summary.documentCount = knowledgeDocumentRepository.count();
        summary.imageCount = imageAssetRepository.count();
        summary.videoCount = videoAssetRepository.count();

        Instant since = Instant.now().minus(1, ChronoUnit.DAYS);
        summary.runCount24h = runs.stream()
                .filter(run -> run.createdAt != null && !run.createdAt.isBefore(since))
                .count();
        summary.failedRunCount24h = runs.stream()
                .filter(run -> run.createdAt != null && !run.createdAt.isBefore(since))
                .filter(this::isFailedRun)
                .count();

        AdminDashboardResponse response = new AdminDashboardResponse();
        response.summary = summary;
        response.health = buildHealth();
        response.recentFailedRuns = runs.stream()
                .filter(this::isFailedRun)
                .limit(5)
                .map(run -> toAdminRun(run, usernameByUserId, knowledgeBaseNameById))
                .collect(Collectors.toList());
        response.recentUsers = users.stream()
                .limit(5)
                .map(authService::toDto)
                .collect(Collectors.toList());
        response.recentDocuments = knowledgeDocumentRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .limit(5)
                .collect(Collectors.toList());
        return response;
    }

    public AdminPageResponse<UserDto> listUsers(String keyword, String role, String status, int page, int size) {
        List<UserDto> items = userRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .filter(user -> matchesKeyword(user, keyword))
                .filter(user -> matchesValue(user.getRole(), role))
                .filter(user -> matchesValue(user.getStatus(), status))
                .map(authService::toDto)
                .collect(Collectors.toList());
        return page(items, page, size);
    }

    public UserDto createUser(AdminCreateUserRequest request) {
        String username = required(request.username, "username");
        String password = required(request.password, "password");
        String role = defaultIfBlank(request.role, "USER");
        String status = defaultIfBlank(request.status, "ACTIVE");

        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("username already exists");
        }

        UserEntity entity = new UserEntity();
        entity.setUserId(UUID.randomUUID().toString());
        entity.setUsername(username);
        entity.setPasswordHash(authService.hashPassword(password));
        entity.setEmail(normalizeNullable(request.email));
        entity.setPhone(normalizeNullable(request.phone));
        entity.setRole(normalizeRole(role));
        entity.setStatus(normalizeStatus(status));
        return authService.toDto(userRepository.save(entity));
    }

    public UserDto updateUser(String userId, AdminUpdateUserRequest request, String currentAdminUserId) {
        UserEntity entity = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));

        if (request.username != null) {
            String username = required(request.username, "username");
            if (!username.equals(entity.getUsername()) && userRepository.existsByUsername(username)) {
                throw new IllegalArgumentException("username already exists");
            }
            entity.setUsername(username);
        }
        if (request.password != null && !request.password.trim().isEmpty()) {
            entity.setPasswordHash(authService.hashPassword(request.password.trim()));
        }
        if (request.email != null) {
            entity.setEmail(normalizeNullable(request.email));
        }
        if (request.phone != null) {
            entity.setPhone(normalizeNullable(request.phone));
        }
        if (request.role != null) {
            String nextRole = normalizeRole(request.role);
            if (entity.getUserId().equals(currentAdminUserId) && !"ADMIN".equals(nextRole)) {
                throw new IllegalArgumentException("cannot remove your own admin role");
            }
            entity.setRole(nextRole);
        }
        if (request.status != null) {
            String nextStatus = normalizeStatus(request.status);
            if (entity.getUserId().equals(currentAdminUserId) && !"ACTIVE".equals(nextStatus)) {
                throw new IllegalArgumentException("cannot disable your own account");
            }
            entity.setStatus(nextStatus);
        }

        return authService.toDto(userRepository.save(entity));
    }

    public void deleteUser(String userId, String currentAdminUserId) {
        UserEntity entity = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));
        if (entity.getUserId().equals(currentAdminUserId)) {
            throw new IllegalArgumentException("cannot delete your own account");
        }
        userRepository.delete(entity);
    }

    public AdminPageResponse<AdminRunListItemDto> listRuns(
            String keyword,
            String userId,
            String capability,
            String status,
            Boolean useKnowledgeBase,
            int page,
            int size) {
        Map<String, String> usernameByUserId = userRepository.findAll().stream()
                .collect(Collectors.toMap(UserEntity::getUserId, UserEntity::getUsername, (left, right) -> left, LinkedHashMap::new));
        Map<String, String> knowledgeBaseNameById = knowledgeBaseRepository.findAll().stream()
                .collect(Collectors.toMap(KnowledgeBaseEntity::getKnowledgeBaseId, KnowledgeBaseEntity::getName, (left, right) -> left, LinkedHashMap::new));

        List<AdminRunListItemDto> items = agentRunRepository.findAllGlobal().stream()
                .filter(run -> userId == null || userId.trim().isEmpty() || userId.equals(runUserId(run.runId)))
                .filter(run -> matchesValue(run.capability, capability))
                .filter(run -> matchesValue(run.status, status))
                .filter(run -> useKnowledgeBase == null || run.useKnowledgeBase == useKnowledgeBase.booleanValue())
                .map(run -> toAdminRun(run, usernameByUserId, knowledgeBaseNameById))
                .filter(item -> matchesRunKeyword(item, keyword))
                .collect(Collectors.toList());

        return page(items, page, size);
    }

    public AgentRun getRun(String runId) {
        return agentRunRepository.findByRunId(runId)
                .orElseThrow(() -> new NoSuchElementException("Agent Run not found: " + runId));
    }

    public List<TraceEvent> getRunTraces(String runId) {
        agentRunRepository.findByRunId(runId)
                .orElseThrow(() -> new NoSuchElementException("Agent Run not found: " + runId));
        List<TraceEvent> traces = agentServiceClient.getTraces(runId);
        agentRunRepository.updateTracesGlobal(runId, traces);
        return traces;
    }

    private String runUserId(String runId) {
        return agentRunRepository.findEntityByRunId(runId)
                .map(entity -> entity.getUserId())
                .orElse(null);
    }

    private boolean isFailedRun(AgentRun run) {
        return run.status != null && !"completed".equalsIgnoreCase(run.status);
    }

    private Map<String, Object> buildHealth() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ok");
        result.put("service", "backend-java");
        try {
            Map<String, Object> agent = agentRunService.agentHealth();
            result.put("agent", agent);
            Object agentStatus = agent.get("status");
            if (agentStatus != null && !"ok".equals(String.valueOf(agentStatus))) {
                result.put("status", "degraded");
            }
        } catch (Exception ex) {
            Map<String, Object> agent = new LinkedHashMap<>();
            agent.put("status", "unavailable");
            agent.put("message", ex.getMessage());
            result.put("agent", agent);
            result.put("status", "degraded");
        }
        return result;
    }

    private AdminRunListItemDto toAdminRun(AgentRun run, Map<String, String> usernameByUserId, Map<String, String> knowledgeBaseNameById) {
        AdminRunListItemDto dto = new AdminRunListItemDto();
        dto.runId = run.runId;
        dto.userId = runUserId(run.runId);
        dto.username = dto.userId == null ? "-" : usernameByUserId.getOrDefault(dto.userId, dto.userId);
        dto.capability = run.capability;
        dto.status = run.status;
        dto.useKnowledgeBase = run.useKnowledgeBase;
        dto.knowledgeBaseId = run.knowledgeBaseId;
        dto.knowledgeBaseName = run.knowledgeBaseId == null ? null : knowledgeBaseNameById.getOrDefault(run.knowledgeBaseId, run.knowledgeBaseId);
        dto.message = run.message;
        dto.createdAt = run.createdAt;
        dto.updatedAt = run.updatedAt;
        return dto;
    }

    private boolean matchesKeyword(UserEntity user, String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase(Locale.ROOT);
        return contains(user.getUsername(), normalized)
                || contains(user.getEmail(), normalized)
                || contains(user.getPhone(), normalized)
                || contains(user.getUserId(), normalized);
    }

    private boolean matchesRunKeyword(AdminRunListItemDto item, String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase(Locale.ROOT);
        return contains(item.runId, normalized)
                || contains(item.username, normalized)
                || contains(item.message, normalized)
                || contains(item.capability, normalized)
                || contains(item.status, normalized);
    }

    private boolean matchesValue(String actual, String expected) {
        if (expected == null || expected.trim().isEmpty()) {
            return true;
        }
        return expected.trim().equalsIgnoreCase(actual == null ? "" : actual);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private String required(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeRole(String role) {
        String normalized = required(role, "role").toUpperCase(Locale.ROOT);
        if (!"USER".equals(normalized) && !"ADMIN".equals(normalized)) {
            throw new IllegalArgumentException("unsupported role: " + role);
        }
        return normalized;
    }

    private String normalizeStatus(String status) {
        String normalized = required(status, "status").toUpperCase(Locale.ROOT);
        if (!"ACTIVE".equals(normalized) && !"DISABLED".equals(normalized)) {
            throw new IllegalArgumentException("unsupported status: " + status);
        }
        return normalized;
    }

    private <T> AdminPageResponse<T> page(List<T> items, int page, int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.max(size, 1);
        int fromIndex = Math.min((normalizedPage - 1) * normalizedSize, items.size());
        int toIndex = Math.min(fromIndex + normalizedSize, items.size());

        AdminPageResponse<T> response = new AdminPageResponse<>();
        response.total = items.size();
        response.page = normalizedPage;
        response.size = normalizedSize;
        response.items = fromIndex >= toIndex ? Collections.emptyList() : new ArrayList<>(items.subList(fromIndex, toIndex));
        return response;
    }
}