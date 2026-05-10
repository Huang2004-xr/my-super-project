package com.example.agentplatform.controller;

import com.example.agentplatform.model.AiProviderEntity;
import com.example.agentplatform.model.ErrorResponse;
import com.example.agentplatform.model.FileAssetEntity;
import com.example.agentplatform.model.ImageAssetEntity;
import com.example.agentplatform.model.KnowledgeBaseEntity;
import com.example.agentplatform.model.KnowledgeDocumentEntity;
import com.example.agentplatform.model.KnowledgeSearchResponse;
import com.example.agentplatform.model.UserEntity;
import com.example.agentplatform.model.VideoAssetEntity;
import com.example.agentplatform.service.AiProviderRepository;
import com.example.agentplatform.service.AuthService;
import com.example.agentplatform.service.CryptoService;
import com.example.agentplatform.service.FileAssetRepository;
import com.example.agentplatform.service.FileStorageService;
import com.example.agentplatform.service.ImageAssetRepository;
import com.example.agentplatform.service.KnowledgeBaseService;
import com.example.agentplatform.service.VideoAssetRepository;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class ResourceController {
    private final AuthService authService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final AiProviderRepository aiProviderRepository;
    private final FileAssetRepository fileAssetRepository;
    private final FileStorageService fileStorageService;
    private final ImageAssetRepository imageAssetRepository;
    private final VideoAssetRepository videoAssetRepository;
    private final CryptoService cryptoService;
    private final com.example.agentplatform.service.AgentRunService agentRunService;

    public ResourceController(AuthService authService, KnowledgeBaseService knowledgeBaseService,
            AiProviderRepository aiProviderRepository,
            FileAssetRepository fileAssetRepository, FileStorageService fileStorageService, ImageAssetRepository imageAssetRepository,
            VideoAssetRepository videoAssetRepository, CryptoService cryptoService,
            com.example.agentplatform.service.AgentRunService agentRunService) {
        this.authService = authService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.aiProviderRepository = aiProviderRepository;
        this.fileAssetRepository = fileAssetRepository;
        this.fileStorageService = fileStorageService;
        this.imageAssetRepository = imageAssetRepository;
        this.videoAssetRepository = videoAssetRepository;
        this.cryptoService = cryptoService;
        this.agentRunService = agentRunService;
    }

    @PostMapping("/knowledge-bases")
    public KnowledgeBaseEntity createKnowledgeBase(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        UserEntity user = authService.requireUser(request);
        return knowledgeBaseService.create(user.getUserId(), required(body, "name"), stringValue(body.get("description")));
    }

    @GetMapping("/knowledge-bases")
    public List<KnowledgeBaseEntity> listKnowledgeBases(HttpServletRequest request) {
        UserEntity user = authService.requireUser(request);
        return knowledgeBaseService.list(user.getUserId());
    }

    @DeleteMapping("/knowledge-bases/{id}")
    public void deleteKnowledgeBase(@PathVariable String id, HttpServletRequest request) {
        UserEntity user = authService.requireUser(request);
        List<String> storagePaths = knowledgeBaseService.delete(user.getUserId(), id);
        knowledgeBaseService.deleteStoredFiles(storagePaths);
    }

    @PostMapping("/knowledge-bases/{id}/documents")
    public KnowledgeDocumentEntity createKnowledgeDocument(@PathVariable String id, @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        throw new IllegalArgumentException("please use /documents/upload to create and index knowledge documents");
    }

    @GetMapping("/knowledge-bases/{id}/documents")
    public List<KnowledgeDocumentEntity> listKnowledgeDocuments(@PathVariable String id, HttpServletRequest request) {
        UserEntity user = authService.requireUser(request);
        return knowledgeBaseService.listDocuments(user.getUserId(), id);
    }

    @PostMapping("/knowledge-bases/{id}/documents/upload")
    public KnowledgeDocumentEntity uploadKnowledgeDocument(
            @PathVariable String id,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) throws IOException {
        UserEntity user = authService.requireUser(request);
        return knowledgeBaseService.uploadDocument(user.getUserId(), id, file);
    }

    @PostMapping("/knowledge-bases/{id}/search")
    public KnowledgeSearchResponse searchKnowledgeBase(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        UserEntity user = authService.requireUser(request);
        int topK = 4;
        if (body.get("topK") != null) {
            topK = Integer.parseInt(String.valueOf(body.get("topK")));
        }
        return knowledgeBaseService.search(user.getUserId(), id, required(body, "query"), topK);
    }

    @PostMapping("/ai-providers")
    public Map<String, Object> createAiProvider(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        UserEntity user = authService.requireUser(request);
        AiProviderEntity entity = new AiProviderEntity();
        entity.setProviderId(UUID.randomUUID().toString());
        entity.setUserId(user.getUserId());
        entity.setName(required(body, "name"));
        entity.setBaseUrl(required(body, "baseUrl"));
        entity.setApiKeyCiphertext(cryptoService.encrypt(required(body, "apiKey")));
        entity.setModelName(stringValue(body.get("modelName")));
        entity.setEnabled(true);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return aiProviderDto(aiProviderRepository.save(entity));
    }

    @GetMapping("/ai-providers")
    public List<Map<String, Object>> listAiProviders(HttpServletRequest request) {
        UserEntity user = authService.requireUser(request);
        return aiProviderRepository.findByUserIdOrderByUpdatedAtDesc(user.getUserId()).stream()
                .map(this::aiProviderDto)
                .collect(java.util.stream.Collectors.toList());
    }

    @PutMapping("/ai-providers/{id}")
    public Map<String, Object> updateAiProvider(@PathVariable String id, @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        UserEntity user = authService.requireUser(request);
        AiProviderEntity entity = aiProviderRepository.findByProviderIdAndUserId(id, user.getUserId())
                .orElseThrow(() -> new NoSuchElementException("AI provider not found: " + id));
        if (body.containsKey("name")) entity.setName(required(body, "name"));
        if (body.containsKey("baseUrl")) entity.setBaseUrl(required(body, "baseUrl"));
        if (body.containsKey("apiKey")) entity.setApiKeyCiphertext(cryptoService.encrypt(required(body, "apiKey")));
        if (body.containsKey("modelName")) entity.setModelName(stringValue(body.get("modelName")));
        if (body.containsKey("enabled")) entity.setEnabled(Boolean.parseBoolean(String.valueOf(body.get("enabled"))));
        entity.setUpdatedAt(Instant.now());
        return aiProviderDto(aiProviderRepository.save(entity));
    }

    @DeleteMapping("/ai-providers/{id}")
    public void deleteAiProvider(@PathVariable String id, HttpServletRequest request) {
        UserEntity user = authService.requireUser(request);
        AiProviderEntity entity = aiProviderRepository.findByProviderIdAndUserId(id, user.getUserId())
                .orElseThrow(() -> new NoSuchElementException("AI provider not found: " + id));
        aiProviderRepository.delete(entity);
    }

    @GetMapping("/assets/images")
    public List<ImageAssetEntity> listImages(HttpServletRequest request) {
        UserEntity user = authService.requireUser(request);
        return imageAssetRepository.findByUserIdOrderByCreatedAtDesc(user.getUserId());
    }

    @GetMapping("/assets/videos")
    public List<VideoAssetEntity> listVideos(HttpServletRequest request) {
        UserEntity user = authService.requireUser(request);
        return videoAssetRepository.findByUserIdOrderByCreatedAtDesc(user.getUserId());
    }

    @PostMapping("/assets/videos")
    public VideoAssetEntity createVideo(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        UserEntity user = authService.requireUser(request);
        VideoAssetEntity entity = new VideoAssetEntity();
        entity.setVideoId(UUID.randomUUID().toString());
        entity.setUserId(user.getUserId());
        entity.setFileAssetId(stringValue(body.get("fileAssetId")));
        entity.setPrompt(stringValue(body.get("prompt")));
        entity.setScript(stringValue(body.get("script")));
        entity.setStatus("CREATED");
        entity.setCreatedAt(Instant.now());
        return videoAssetRepository.save(entity);
    }

    @PostMapping("/assets/videos/upload")
    public void uploadVideo(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        UserEntity user = authService.requireUser(request);
        agentRunService.saveUploadedVideo(user.getUserId(), file);
    }

    @GetMapping("/assets/files/{assetType}")
    public List<FileAssetEntity> listFiles(@PathVariable String assetType, HttpServletRequest request) {
        UserEntity user = authService.requireUser(request);
        return fileAssetRepository.findByUserIdAndAssetTypeOrderByCreatedAtDesc(user.getUserId(), assetType);
    }

    @GetMapping("/assets/files/{fileAssetId}/content")
    public ResponseEntity<Resource> fileContent(@PathVariable String fileAssetId, HttpServletRequest request)
            throws IOException {
        UserEntity user = authService.requireUser(request);
        FileAssetEntity entity = fileAssetRepository.findByFileAssetIdAndUserId(fileAssetId, user.getUserId())
                .orElseThrow(() -> new NoSuchElementException("File asset not found: " + fileAssetId));
        Path path = fileStorageService.resolveStoredPath(entity.getStoragePath());
        Resource resource = new UrlResource(path.toUri());
        return ResponseEntity.ok()
                .contentType(safeMediaType(entity.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + safeHeaderFileName(entity.getFileName()) + "\"")
                .body(resource);
    }

    private String required(Map<String, Object> body, String key) {
        String value = stringValue(body.get(key));
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value.trim();
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String safeHeaderFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return "download";
        }
        return fileName.replace("\\", "_")
                .replace("/", "_")
                .replace("\"", "'")
                .replace("\r", "_")
                .replace("\n", "_");
    }

    private MediaType safeMediaType(String contentType) {
        if (contentType == null || contentType.trim().isEmpty()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (IllegalArgumentException ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private Map<String, Object> aiProviderDto(AiProviderEntity entity) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("providerId", entity.getProviderId());
        dto.put("userId", entity.getUserId());
        dto.put("name", entity.getName());
        dto.put("baseUrl", entity.getBaseUrl());
        dto.put("modelName", entity.getModelName());
        dto.put("enabled", entity.isEnabled());
        dto.put("createdAt", entity.getCreatedAt());
        dto.put("updatedAt", entity.getUpdatedAt());
        dto.put("apiKeySet", entity.getApiKeyCiphertext() != null && !entity.getApiKeyCiphertext().isEmpty());
        return dto;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> badRequest(Exception ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(error("BAD_REQUEST", ex, request));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> notFound(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error("NOT_FOUND", ex, request));
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<ErrorResponse> fileError(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error("FILE_ERROR", ex, request));
    }

    private ErrorResponse error(String code, Exception ex, HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null || requestId.trim().isEmpty()) {
            requestId = UUID.randomUUID().toString();
        }
        return new ErrorResponse(code, ex.getMessage(), requestId);
    }
}
