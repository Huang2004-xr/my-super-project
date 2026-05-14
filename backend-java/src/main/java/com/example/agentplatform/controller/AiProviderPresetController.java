package com.example.agentplatform.controller;

import com.example.agentplatform.dto.AiProviderPresetResponse;
import com.example.agentplatform.model.UserEntity;
import com.example.agentplatform.service.AiProviderPresetService;
import com.example.agentplatform.service.AuthService;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai-provider-presets")
public class AiProviderPresetController {
    private final AuthService authService;
    private final AiProviderPresetService presetService;

    public AiProviderPresetController(AuthService authService, AiProviderPresetService presetService) {
        this.authService = authService;
        this.presetService = presetService;
    }

    @GetMapping
    public List<AiProviderPresetResponse> list(HttpServletRequest request) {
        UserEntity user = authService.requireUser(request);
        return presetService.list();
    }
}
