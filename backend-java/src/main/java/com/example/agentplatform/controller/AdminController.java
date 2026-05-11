package com.example.agentplatform.controller;

import com.example.agentplatform.dto.admin.AdminDashboardResponse;
import com.example.agentplatform.dto.admin.AdminCreateUserRequest;
import com.example.agentplatform.dto.admin.AdminPageResponse;
import com.example.agentplatform.dto.admin.AdminRunListItemDto;
import com.example.agentplatform.dto.admin.AdminUpdateUserRequest;
import com.example.agentplatform.model.AgentRun;
import com.example.agentplatform.model.TraceEvent;
import com.example.agentplatform.model.UserDto;
import com.example.agentplatform.model.UserEntity;
import com.example.agentplatform.service.AdminQueryService;
import com.example.agentplatform.service.AuthService;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AuthService authService;
    private final AdminQueryService adminQueryService;

    public AdminController(AuthService authService, AdminQueryService adminQueryService) {
        this.authService = authService;
        this.adminQueryService = adminQueryService;
    }

    @GetMapping("/dashboard")
    public AdminDashboardResponse dashboard(HttpServletRequest request) {
        authService.requireAdmin(request);
        return adminQueryService.getDashboard();
    }

    @GetMapping("/users")
    public AdminPageResponse<UserDto> users(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        authService.requireAdmin(request);
        return adminQueryService.listUsers(keyword, role, status, page, size);
    }

    @PostMapping("/users")
    public UserDto createUser(@RequestBody AdminCreateUserRequest body, HttpServletRequest request) {
        authService.requireAdmin(request);
        return adminQueryService.createUser(body);
    }

    @PutMapping("/users/{userId}")
    public UserDto updateUser(@PathVariable String userId, @RequestBody AdminUpdateUserRequest body, HttpServletRequest request) {
        UserEntity currentUser = authService.requireAdmin(request);
        return adminQueryService.updateUser(userId, body, currentUser.getUserId());
    }

    @DeleteMapping("/users/{userId}")
    public void deleteUser(@PathVariable String userId, HttpServletRequest request) {
        UserEntity currentUser = authService.requireAdmin(request);
        adminQueryService.deleteUser(userId, currentUser.getUserId());
    }

    @GetMapping("/agent-runs")
    public AdminPageResponse<AdminRunListItemDto> runs(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String capability,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean useKnowledgeBase,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        authService.requireAdmin(request);
        return adminQueryService.listRuns(keyword, userId, capability, status, useKnowledgeBase, page, size);
    }

    @GetMapping("/agent-runs/{runId}")
    public AgentRun run(@PathVariable String runId, HttpServletRequest request) {
        authService.requireAdmin(request);
        return adminQueryService.getRun(runId);
    }

    @GetMapping("/agent-runs/{runId}/traces")
    public List<TraceEvent> traces(@PathVariable String runId, HttpServletRequest request) {
        authService.requireAdmin(request);
        return adminQueryService.getRunTraces(runId);
    }
}
