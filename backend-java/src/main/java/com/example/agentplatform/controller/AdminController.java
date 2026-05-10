package com.example.agentplatform.controller;

import com.example.agentplatform.model.UserDto;
import com.example.agentplatform.model.UserEntity;
import com.example.agentplatform.service.AuthService;
import com.example.agentplatform.service.UserRepository;
import java.util.List;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AuthService authService;
    private final UserRepository userRepository;

    public AdminController(AuthService authService, UserRepository userRepository) {
        this.authService = authService;
        this.userRepository = userRepository;
    }

    @GetMapping("/users")
    public List<UserDto> users(HttpServletRequest request) {
        authService.requireAdmin(request);
        return userRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private UserDto toDto(UserEntity user) {
        return authService.toDto(user);
    }
}
