package com.example.agentplatform.service;

import com.example.agentplatform.dto.CreateAgentRunRequest;
import com.example.agentplatform.model.AgentRun;
import com.example.agentplatform.model.CapabilityDefinition;
import com.example.agentplatform.model.FileAssetEntity;
import com.example.agentplatform.model.ImageAsset;
import com.example.agentplatform.model.ImageAssetEntity;
import com.example.agentplatform.model.KnowledgeSearchResponse;
import com.example.agentplatform.model.TraceEvent;
import com.example.agentplatform.model.VideoAssetEntity;
import java.time.Instant;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AgentRunService {
    private static final Set<String> CAPABILITY_HINTS = new HashSet<>(Arrays.asList(
            "TEXT_CHAT", "VIDEO_CREATION", "IMAGE_CREATION", "KNOWLEDGE_RETRIEVAL"));

    private final AgentRunRepository repository;
    private final AgentServiceClient agentClient;
    private final ConversationService conversationService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final FileAssetRepository fileAssetRepository;
    private final FileStorageService fileStorageService;
    private final ImageAssetRepository imageAssetRepository;
    private final VideoAssetRepository videoAssetRepository;
    private final long maxImageBytes;
    private final long maxVideoBytes;

    public AgentRunService(AgentRunRepository repository, AgentServiceClient agentClient,
            ConversationService conversationService, KnowledgeBaseService knowledgeBaseService,
            FileAssetRepository fileAssetRepository, FileStorageService fileStorageService,
            ImageAssetRepository imageAssetRepository, VideoAssetRepository videoAssetRepository,
            @Value("${upload.max-image-bytes}") long maxImageBytes,
            @Value("${upload.max-video-bytes}") long maxVideoBytes) {
        this.repository = repository;
        this.agentClient = agentClient;
        this.conversationService = conversationService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.fileAssetRepository = fileAssetRepository;
        this.fileStorageService = fileStorageService;
        this.imageAssetRepository = imageAssetRepository;
        this.videoAssetRepository = videoAssetRepository;
        this.maxImageBytes = maxImageBytes;
        this.maxVideoBytes = maxVideoBytes;
    }

    public Map<String, Object> agentHealth() {
        return agentClient.health();
    }

    public List<CapabilityDefinition> getCapabilities() {
        return agentClient.getCapabilities();
    }

    public AgentRun createRun(String userId, CreateAgentRunRequest request) {
        if (request.message == null || request.message.trim().isEmpty()) {
            throw new IllegalArgumentException("message is required");
        }
        request.message = request.message.trim();
        if (request.knowledgeBaseId == null && request.useKnowledgeBase) {
            request.knowledgeBaseId = "builtin";
        }
        if (request.capabilityHint != null && !request.capabilityHint.trim().isEmpty()
                && !CAPABILITY_HINTS.contains(request.capabilityHint)) {
            throw new IllegalArgumentException("unsupported capabilityHint: " + request.capabilityHint);
        }
        if (request.input == null) {
            request.input = new java.util.LinkedHashMap<>();
        }
        if (request.useKnowledgeBase) {
            KnowledgeSearchResponse knowledgeSearch = knowledgeBaseService.search(userId, request.knowledgeBaseId, request.message, 6);
            request.input.put("knowledgeHits", knowledgeSearch.hits);
            request.input.put("knowledgeHitCount", knowledgeSearch.hits == null ? 0 : knowledgeSearch.hits.size());
        }
        conversationService.ensureExists(userId, request.conversationId);
        request.input.putAll(conversationService.prepareMemoryInput(userId, request.conversationId));
        AgentRun run = agentClient.startRun(request);
        AgentRun saved = repository.save(run, userId, request.conversationId);
        conversationService.attachRun(userId, request.conversationId, request.message, saved.capability,
                saved.finalResult, saved.runId);
        saveGeneratedAsset(userId, saved);
        return saved;
    }

    public List<AgentRun> listRuns(String userId) {
        return repository.findAll(userId);
    }

    public AgentRun getRun(String userId, String runId) {
        return repository.findById(userId, runId)
                .orElseThrow(() -> new NoSuchElementException("Agent Run not found: " + runId));
    }

    public List<TraceEvent> getTraces(String userId, String runId) {
        repository.findById(userId, runId).ifPresent(run -> {
            List<TraceEvent> traces = agentClient.getTraces(runId);
            run.traces = traces;
            repository.updateTraces(userId, runId, traces);
        });
        return repository.findById(userId, runId)
                .map(run -> run.traces)
                .orElseThrow(() -> new NoSuchElementException("Agent Run not found: " + runId));
    }

    public ImageAsset uploadImage(String userId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("image file is required");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType();
        if (!contentType.startsWith("image/")) {
            throw new IllegalArgumentException("only image files are supported");
        }
        requireMaxSize(file, maxImageBytes, "image");
        ImageAsset image = agentClient.uploadImage(file);
        FileAssetEntity fileAsset = new FileAssetEntity();
        fileAsset.setFileAssetId(image.imageAssetId);
        fileAsset.setUserId(userId);
        fileAsset.setAssetType("IMAGE");
        fileAsset.setFileName(image.fileName);
        fileAsset.setContentType(image.contentType);
        fileAsset.setStoragePath(fileStorageService.store(file, userId, "IMAGE", image.imageAssetId));
        fileAsset.setSizeBytes(file.getSize());
        fileAsset.setCreatedAt(Instant.now());
        fileAssetRepository.save(fileAsset);

        ImageAssetEntity imageAsset = new ImageAssetEntity();
        imageAsset.setImageId(UUID.randomUUID().toString());
        imageAsset.setUserId(userId);
        imageAsset.setFileAssetId(image.imageAssetId);
        imageAsset.setSource("UPLOAD");
        imageAsset.setCreatedAt(Instant.now());
        imageAssetRepository.save(imageAsset);
        return image;
    }

    public void saveUploadedVideo(String userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("video file is required");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType();
        if (!contentType.startsWith("video/")) {
            throw new IllegalArgumentException("only video files are supported");
        }
        requireMaxSize(file, maxVideoBytes, "video");
        String fileAssetId = UUID.randomUUID().toString();
        FileAssetEntity fileAsset = new FileAssetEntity();
        fileAsset.setFileAssetId(fileAssetId);
        fileAsset.setUserId(userId);
        fileAsset.setAssetType("VIDEO");
        fileAsset.setFileName(file.getOriginalFilename() == null ? "uploaded-video" : file.getOriginalFilename());
        fileAsset.setContentType(contentType);
        try {
            fileAsset.setStoragePath(fileStorageService.store(file, userId, "VIDEO", fileAssetId));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to store video file", ex);
        }
        fileAsset.setSizeBytes(file.getSize());
        fileAsset.setCreatedAt(Instant.now());
        fileAssetRepository.save(fileAsset);

        VideoAssetEntity videoAsset = new VideoAssetEntity();
        videoAsset.setVideoId(UUID.randomUUID().toString());
        videoAsset.setUserId(userId);
        videoAsset.setFileAssetId(fileAssetId);
        videoAsset.setStatus("UPLOADED");
        videoAsset.setCreatedAt(Instant.now());
        videoAssetRepository.save(videoAsset);
    }

    private void saveGeneratedAsset(String userId, AgentRun run) {
        if ("IMAGE_CREATION".equals(run.capability)) {
            String fileAssetId = UUID.randomUUID().toString();
            FileAssetEntity fileAsset = new FileAssetEntity();
            fileAsset.setFileAssetId(fileAssetId);
            fileAsset.setUserId(userId);
            fileAsset.setAssetType("IMAGE");
            fileAsset.setFileName("generated-" + run.runId + ".png");
            fileAsset.setContentType("image/png");
            fileAsset.setStoragePath("mock-generated://" + run.runId);
            fileAsset.setCreatedAt(Instant.now());
            fileAssetRepository.save(fileAsset);

            ImageAssetEntity imageAsset = new ImageAssetEntity();
            imageAsset.setImageId(UUID.randomUUID().toString());
            imageAsset.setUserId(userId);
            imageAsset.setFileAssetId(fileAssetId);
            imageAsset.setPrompt(run.message);
            imageAsset.setSource("GENERATED");
            imageAsset.setCreatedAt(Instant.now());
            imageAssetRepository.save(imageAsset);
        }
        if ("VIDEO_CREATION".equals(run.capability)) {
            String fileAssetId = UUID.randomUUID().toString();
            FileAssetEntity fileAsset = new FileAssetEntity();
            fileAsset.setFileAssetId(fileAssetId);
            fileAsset.setUserId(userId);
            fileAsset.setAssetType("VIDEO");
            fileAsset.setFileName("generated-" + run.runId + ".mp4");
            fileAsset.setContentType("video/mp4");
            fileAsset.setStoragePath("mock-generated://" + run.runId);
            fileAsset.setCreatedAt(Instant.now());
            fileAssetRepository.save(fileAsset);

            VideoAssetEntity videoAsset = new VideoAssetEntity();
            videoAsset.setVideoId(UUID.randomUUID().toString());
            videoAsset.setUserId(userId);
            videoAsset.setFileAssetId(fileAssetId);
            videoAsset.setPrompt(run.message);
            videoAsset.setScript(run.finalResult);
            videoAsset.setStatus("GENERATED");
            videoAsset.setCreatedAt(Instant.now());
            videoAssetRepository.save(videoAsset);
        }
    }

    private void requireMaxSize(MultipartFile file, long maxBytes, String label) {
        if (maxBytes > 0 && file.getSize() > maxBytes) {
            throw new IllegalArgumentException(label + " file exceeds max size: " + maxBytes + " bytes");
        }
    }
}
