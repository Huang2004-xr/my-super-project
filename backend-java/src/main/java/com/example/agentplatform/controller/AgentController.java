package com.example.agentplatform.controller;

import com.example.agentplatform.dto.CreateAgentRunRequest;
import com.example.agentplatform.model.AgentRun;
import com.example.agentplatform.model.CapabilityDefinition;
import com.example.agentplatform.model.ErrorResponse;
import com.example.agentplatform.model.ImageAsset;
import com.example.agentplatform.model.TraceEvent;
import com.example.agentplatform.model.UserEntity;
import com.example.agentplatform.service.AgentRunService;
import com.example.agentplatform.service.AuthService;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class AgentController {
    private final AgentRunService service;
    private final AuthService authService;
    private final int port;

    public AgentController(AgentRunService service, AuthService authService, @Value("${server.port}") int port) {
        this.service = service;
        this.authService = authService;
        this.port = port;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ok");
        result.put("service", "backend-java");
        result.put("port", port);
        try {
            Map<String, Object> agent = service.agentHealth();
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

    @GetMapping("/capabilities")
    public List<CapabilityDefinition> capabilities() {
        return service.getCapabilities();
    }

    @PostMapping("/agent-runs")
    public AgentRun createRun(@RequestBody CreateAgentRunRequest request, HttpServletRequest servletRequest) {
        UserEntity user = authService.requireUser(servletRequest);
        return service.createRun(user.getUserId(), request);
    }

    @GetMapping("/agent-runs")
    public List<AgentRun> listRuns(HttpServletRequest request) {
        UserEntity user = authService.requireUser(request);
        return service.listRuns(user.getUserId());
    }

    @GetMapping("/agent-runs/{runId}")
    public AgentRun getRun(@PathVariable String runId, HttpServletRequest request) {
        UserEntity user = authService.requireUser(request);
        return service.getRun(user.getUserId(), runId);
    }

    @GetMapping("/traces/{runId}")
    public List<TraceEvent> traces(@PathVariable String runId, HttpServletRequest request) {
        UserEntity user = authService.requireUser(request);
        return service.getTraces(user.getUserId(), runId);
    }

    @PostMapping("/assets/images")
    public ImageAsset uploadImage(@RequestParam("file") MultipartFile file, HttpServletRequest request) throws IOException {
        UserEntity user = authService.requireUser(request);
        return service.uploadImage(user.getUserId(), file);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> badRequest(Exception ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(error("BAD_REQUEST", ex, request));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> notFound(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error("NOT_FOUND", ex, request));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> serverError(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error("INTERNAL_ERROR", ex, request));
    }

    private ErrorResponse error(String code, Exception ex, HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null || requestId.trim().isEmpty()) {
            requestId = UUID.randomUUID().toString();
        }
        return new ErrorResponse(code, ex.getMessage(), requestId);
    }
}
