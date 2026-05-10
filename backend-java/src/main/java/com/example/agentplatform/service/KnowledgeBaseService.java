package com.example.agentplatform.service;

import com.example.agentplatform.model.FileAssetEntity;
import com.example.agentplatform.model.KnowledgeBaseEntity;
import com.example.agentplatform.model.KnowledgeDocumentEntity;
import com.example.agentplatform.model.KnowledgeSearchResponse;
import java.io.IOException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class KnowledgeBaseService {
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final KnowledgeChunkRepository knowledgeChunkRepository;
    private final FileAssetRepository fileAssetRepository;
    private final FileStorageService fileStorageService;
    private final KnowledgeIndexWorkerService knowledgeIndexWorkerService;
    private final AgentServiceClient agentServiceClient;
    private final long maxDocumentBytes;

    public KnowledgeBaseService(
            KnowledgeBaseRepository knowledgeBaseRepository,
            KnowledgeDocumentRepository knowledgeDocumentRepository,
            KnowledgeChunkRepository knowledgeChunkRepository,
            FileAssetRepository fileAssetRepository,
            FileStorageService fileStorageService,
            KnowledgeIndexWorkerService knowledgeIndexWorkerService,
            AgentServiceClient agentServiceClient,
            @Value("${upload.max-document-bytes}") long maxDocumentBytes) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.knowledgeChunkRepository = knowledgeChunkRepository;
        this.fileAssetRepository = fileAssetRepository;
        this.fileStorageService = fileStorageService;
        this.knowledgeIndexWorkerService = knowledgeIndexWorkerService;
        this.agentServiceClient = agentServiceClient;
        this.maxDocumentBytes = maxDocumentBytes;
    }

    public KnowledgeBaseEntity create(String userId, String name, String description) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("name is required");
        }
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setKnowledgeBaseId(UUID.randomUUID().toString());
        entity.setUserId(userId);
        entity.setName(name.trim());
        entity.setDescription(description == null ? null : description.trim());
        entity.setStatus("ACTIVE");
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return knowledgeBaseRepository.save(entity);
    }

    public List<KnowledgeBaseEntity> list(String userId) {
        return knowledgeBaseRepository.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    @Transactional
    public List<String> delete(String userId, String knowledgeBaseId) {
        KnowledgeBaseEntity knowledgeBase = requireKnowledgeBase(userId, knowledgeBaseId);
        List<KnowledgeDocumentEntity> documents = knowledgeDocumentRepository.findByKnowledgeBaseIdAndUserId(knowledgeBaseId, userId);
        List<String> fileAssetIds = new ArrayList<>();
        for (KnowledgeDocumentEntity document : documents) {
            if (document.getFileAssetId() != null && !document.getFileAssetId().trim().isEmpty()) {
                fileAssetIds.add(document.getFileAssetId());
            }
        }
        List<String> storagePaths = new ArrayList<>();
        if (!fileAssetIds.isEmpty()) {
            List<FileAssetEntity> fileAssets = fileAssetRepository.findByUserIdAndAssetTypeAndFileAssetIdIn(userId, "DOCUMENT", fileAssetIds);
            for (FileAssetEntity fileAsset : fileAssets) {
                if (fileAsset.getStoragePath() != null && !fileAsset.getStoragePath().trim().isEmpty()) {
                    storagePaths.add(fileAsset.getStoragePath());
                }
            }
            fileAssetRepository.deleteAll(fileAssets);
        }
        knowledgeChunkRepository.deleteByKnowledgeBaseIdAndUserId(knowledgeBaseId, userId);
        knowledgeDocumentRepository.deleteByKnowledgeBaseIdAndUserId(knowledgeBaseId, userId);
        knowledgeBaseRepository.delete(knowledgeBase);
        return storagePaths;
    }

    public void deleteStoredFiles(List<String> storagePaths) {
        for (String storagePath : storagePaths) {
            try {
                fileStorageService.deleteStoredPath(storagePath);
            } catch (IOException ex) {
                System.err.println("Failed to delete knowledge document file: " + storagePath + "; " + ex.getMessage());
            }
        }
    }

    public List<KnowledgeDocumentEntity> listDocuments(String userId, String knowledgeBaseId) {
        requireKnowledgeBase(userId, knowledgeBaseId);
        List<KnowledgeDocumentEntity> documents = knowledgeDocumentRepository.findByKnowledgeBaseIdAndUserIdOrderByCreatedAtDesc(knowledgeBaseId, userId);
        hydrateDocumentSizes(userId, documents);
        return documents;
    }

    public KnowledgeDocumentEntity uploadDocument(String userId, String knowledgeBaseId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("document file is required");
        }
        requireMaxSize(file, maxDocumentBytes, "document");
        KnowledgeBaseEntity knowledgeBase = requireKnowledgeBase(userId, knowledgeBaseId);
        byte[] fileContent = file.getBytes();
        String fileName = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename();
        String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        FileAssetEntity fileAsset = new FileAssetEntity();
        fileAsset.setFileAssetId(UUID.randomUUID().toString());
        fileAsset.setUserId(userId);
        fileAsset.setAssetType("DOCUMENT");
        fileAsset.setFileName(fileName);
        fileAsset.setContentType(contentType);
        fileAsset.setStoragePath(fileStorageService.store(file, userId, "DOCUMENT", fileAsset.getFileAssetId()));
        fileAsset.setSizeBytes(file.getSize());
        fileAsset.setCreatedAt(Instant.now());
        fileAssetRepository.save(fileAsset);

        KnowledgeDocumentEntity document = new KnowledgeDocumentEntity();
        document.setDocumentId(UUID.randomUUID().toString());
        document.setKnowledgeBaseId(knowledgeBaseId);
        document.setUserId(userId);
        document.setFileAssetId(fileAsset.getFileAssetId());
        document.setFileName(fileName);
        document.setFileType(fileType(fileName));
        document.setMimeType(contentType);
        document.setSizeBytes(fileAsset.getSizeBytes());
        document.setParseStatus("QUEUED");
        document.setIndexStatus("PENDING");
        document.setChunkCount(0);
        document.setContentDigest(digest(fileContent));
        document.setCreatedAt(Instant.now());
        document.setUpdatedAt(Instant.now());
        KnowledgeDocumentEntity saved = knowledgeDocumentRepository.save(document);
        knowledgeBase.setUpdatedAt(Instant.now());
        knowledgeBaseRepository.save(knowledgeBase);
        knowledgeIndexWorkerService.indexDocumentAsync(userId, knowledgeBaseId, saved.getDocumentId());
        return saved;
    }

    private void hydrateDocumentSizes(String userId, List<KnowledgeDocumentEntity> documents) {
        List<String> fileAssetIds = new ArrayList<>();
        for (KnowledgeDocumentEntity document : documents) {
            if (document.getFileAssetId() != null && !document.getFileAssetId().trim().isEmpty()) {
                fileAssetIds.add(document.getFileAssetId());
            }
        }
        if (fileAssetIds.isEmpty()) {
            return;
        }

        List<FileAssetEntity> fileAssets = fileAssetRepository.findByUserIdAndAssetTypeAndFileAssetIdIn(userId, "DOCUMENT", fileAssetIds);
        Map<String, Long> sizeByAssetId = new HashMap<>();
        for (FileAssetEntity fileAsset : fileAssets) {
            sizeByAssetId.put(fileAsset.getFileAssetId(), fileAsset.getSizeBytes());
        }

        for (KnowledgeDocumentEntity document : documents) {
            if (document.getFileAssetId() == null) {
              continue;
            }
            document.setSizeBytes(sizeByAssetId.get(document.getFileAssetId()));
        }
    }

    public KnowledgeSearchResponse search(String userId, String knowledgeBaseId, String query, int topK) {
        requireKnowledgeBase(userId, knowledgeBaseId);
        KnowledgeSearchResponse response = agentServiceClient.searchKnowledge(userId, knowledgeBaseId, query, topK);
        if (response.hits == null) {
            response.hits = new ArrayList<>();
        }
        return response;
    }

    public KnowledgeBaseEntity requireKnowledgeBase(String userId, String knowledgeBaseId) {
        if (knowledgeBaseId == null || knowledgeBaseId.trim().isEmpty()) {
            throw new IllegalArgumentException("knowledgeBaseId is required");
        }
        return knowledgeBaseRepository.findByKnowledgeBaseIdAndUserId(knowledgeBaseId, userId)
                .orElseThrow(() -> new NoSuchElementException("Knowledge base not found: " + knowledgeBaseId));
    }

    private String fileType(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index >= 0 ? fileName.substring(index + 1).toUpperCase() : "TEXT";
    }

    private String digest(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(content);
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to digest content", ex);
        }
    }

    private void requireMaxSize(MultipartFile file, long maxBytes, String label) {
        if (maxBytes > 0 && file.getSize() > maxBytes) {
            throw new IllegalArgumentException(label + " file exceeds max size: " + maxBytes + " bytes");
        }
    }
}
