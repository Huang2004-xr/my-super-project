package com.example.agentplatform.controller;

import com.example.agentplatform.dto.AiProviderRequest;
import com.example.agentplatform.dto.AiProviderResponse;
import com.example.agentplatform.dto.AiProviderTestResponse;
import com.example.agentplatform.model.ErrorResponse;
import com.example.agentplatform.model.UserEntity;
import com.example.agentplatform.service.AiProviderService;
import com.example.agentplatform.service.AuthService;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai-providers")
public class AiProviderController {
    private final AuthService authService;
    private final AiProviderService aiProviderService;

    public AiProviderController(AuthService authService, AiProviderService aiProviderService) {
        this.authService = authService;
        this.aiProviderService = aiProviderService;
    }

    @GetMapping
    public List<AiProviderResponse> list(HttpServletRequest request) {
        UserEntity user = authService.requireUser(request);
        return aiProviderService.list(user.getUserId());
    }

    @PostMapping
    public AiProviderResponse create(@RequestBody AiProviderRequest body, HttpServletRequest request) {
        UserEntity user = authService.requireUser(request);
        return aiProviderService.create(user.getUserId(), body);
    }

    @PutMapping("/{id}")
    public AiProviderResponse update(@PathVariable String id, @RequestBody AiProviderRequest body,
            HttpServletRequest request) {
        UserEntity user = authService.requireUser(request);
        return aiProviderService.update(user.getUserId(), id, body);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id, HttpServletRequest request) {
        UserEntity user = authService.requireUser(request);
        aiProviderService.delete(user.getUserId(), id);
    }

    @PostMapping("/{id}/test")
    public AiProviderTestResponse test(@PathVariable String id, HttpServletRequest request) {
        UserEntity user = authService.requireUser(request);
        return aiProviderService.test(user.getUserId(), id);
    }

    @GetMapping("/effective")
    public Map<String, Object> effective(HttpServletRequest request) {
        UserEntity user = authService.requireUser(request);
        return aiProviderService.effective(user.getUserId());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> badRequest(Exception ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(error("BAD_REQUEST", ex, request));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> notFound(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error("NOT_FOUND", ex, request));
    }

    private ErrorResponse error(String code, Exception ex, HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null || requestId.trim().isEmpty()) {
            requestId = UUID.randomUUID().toString();
        }
        return new ErrorResponse(code, ex.getMessage(), requestId);
    }
}
