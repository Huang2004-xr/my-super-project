package com.example.agentplatform.controller;

import com.example.agentplatform.model.ConversationDto;
import com.example.agentplatform.model.ConversationMessageDto;
import com.example.agentplatform.model.ErrorResponse;
import com.example.agentplatform.model.UserEntity;
import com.example.agentplatform.service.AuthService;
import com.example.agentplatform.service.ConversationService;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {
    private final ConversationService service;
    private final AuthService authService;

    public ConversationController(ConversationService service, AuthService authService) {
        this.service = service;
        this.authService = authService;
    }

    @PostMapping
    public ConversationDto create(HttpServletRequest request) {
        UserEntity user = authService.requireUser(request);
        return service.create(user.getUserId());
    }

    @GetMapping
    public List<ConversationDto> list(HttpServletRequest request) {
        UserEntity user = authService.requireUser(request);
        return service.findByUserId(user.getUserId());
    }

    @GetMapping("/{conversationId}")
    public ConversationDto get(@PathVariable String conversationId, HttpServletRequest request) {
        UserEntity user = authService.requireUser(request);
        return service.get(user.getUserId(), conversationId);
    }

    @GetMapping("/{conversationId}/messages")
    public List<ConversationMessageDto> messages(@PathVariable String conversationId, HttpServletRequest request) {
        UserEntity user = authService.requireUser(request);
        return service.getMessages(user.getUserId(), conversationId);
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
