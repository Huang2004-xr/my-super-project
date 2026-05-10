package com.example.agentplatform.service;

import com.example.agentplatform.model.FileAssetEntity;
import com.example.agentplatform.model.KnowledgeBaseEntity;
import com.example.agentplatform.model.KnowledgeChunkEntity;
import com.example.agentplatform.model.KnowledgeDocumentEntity;
import com.example.agentplatform.model.KnowledgeIndexChunk;
import com.example.agentplatform.model.KnowledgeIndexResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class KnowledgeIndexWorkerService {
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final KnowledgeChunkRepository knowledgeChunkRepository;
    private final FileAssetRepository fileAssetRepository;
    private final FileStorageService fileStorageService;
    private final AgentServiceClient agentServiceClient;
    private final TransactionTemplate transactionTemplate;

    public KnowledgeIndexWorkerService(
            KnowledgeBaseRepository knowledgeBaseRepository,
            KnowledgeDocumentRepository knowledgeDocumentRepository,
            KnowledgeChunkRepository knowledgeChunkRepository,
            FileAssetRepository fileAssetRepository,
            FileStorageService fileStorageService,
            AgentServiceClient agentServiceClient,
            TransactionTemplate transactionTemplate) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.knowledgeChunkRepository = knowledgeChunkRepository;
        this.fileAssetRepository = fileAssetRepository;
        this.fileStorageService = fileStorageService;
        this.agentServiceClient = agentServiceClient;
        this.transactionTemplate = transactionTemplate;
    }

    @Async
    public void indexDocumentAsync(String userId, String knowledgeBaseId, String documentId) {
        KnowledgeDocumentEntity document = null;
        try {
            document = knowledgeDocumentRepository.findByDocumentIdAndUserId(documentId, userId)
                    .orElseThrow(() -> new NoSuchElementException("Knowledge document not found: " + documentId));
            markProcessing(document);

            String fileAssetId = document.getFileAssetId();
            FileAssetEntity fileAsset = fileAssetRepository.findByFileAssetIdAndUserId(fileAssetId, userId)
                    .orElseThrow(() -> new NoSuchElementException("File asset not found: " + fileAssetId));
            Path path = fileStorageService.resolveStoredPath(fileAsset.getStoragePath());
            KnowledgeIndexResponse indexResponse = agentServiceClient.indexKnowledgeDocument(
                    userId,
                    knowledgeBaseId,
                    documentId,
                    fileAsset.getFileName(),
                    Files.readAllBytes(path));

            if (indexResponse == null) {
                throw new IllegalStateException("Python Agent returned empty knowledge index response");
            }
            saveIndexedResult(document, userId, knowledgeBaseId, fileAsset.getFileName(), indexResponse);
        } catch (Exception ex) {
            markFailed(document, userId, documentId, ex);
        }
    }

    private void markProcessing(KnowledgeDocumentEntity document) {
        transactionTemplate.execute(status -> {
            document.setParseStatus("PROCESSING");
            document.setIndexStatus("INDEXING");
            document.setErrorReason(null);
            document.setUpdatedAt(Instant.now());
            knowledgeDocumentRepository.save(document);
            return null;
        });
    }

    private void saveIndexedResult(
            KnowledgeDocumentEntity document,
            String userId,
            String knowledgeBaseId,
            String fileName,
            KnowledgeIndexResponse indexResponse) {
        transactionTemplate.execute(status -> {
            knowledgeChunkRepository.deleteByDocumentIdAndUserId(document.getDocumentId(), userId);
            knowledgeChunkRepository.saveAll(toChunkEntities(
                    userId,
                    knowledgeBaseId,
                    fileName,
                    document.getDocumentId(),
                    indexResponse.chunks));
            document.setParseStatus("COMPLETED");
            document.setIndexStatus(indexResponse.status == null ? "INDEXED" : indexResponse.status);
            document.setChunkCount(indexResponse.chunkCount);
            document.setErrorReason(null);
            document.setUpdatedAt(Instant.now());
            knowledgeDocumentRepository.save(document);

            KnowledgeBaseEntity knowledgeBase = knowledgeBaseRepository.findByKnowledgeBaseIdAndUserId(knowledgeBaseId, userId)
                    .orElseThrow(() -> new NoSuchElementException("Knowledge base not found: " + knowledgeBaseId));
            knowledgeBase.setUpdatedAt(Instant.now());
            knowledgeBaseRepository.save(knowledgeBase);
            return null;
        });
    }

    private void markFailed(KnowledgeDocumentEntity document, String userId, String documentId, Exception ex) {
        transactionTemplate.execute(status -> {
            KnowledgeDocumentEntity target = document;
            if (target == null) {
                target = knowledgeDocumentRepository.findByDocumentIdAndUserId(documentId, userId).orElse(null);
            }
            if (target == null) {
                return null;
            }
            target.setParseStatus("FAILED");
            target.setIndexStatus("FAILED");
            target.setErrorReason(truncate(errorMessage(ex), 2000));
            target.setUpdatedAt(Instant.now());
            knowledgeDocumentRepository.save(target);
            return null;
        });
    }

    private List<KnowledgeChunkEntity> toChunkEntities(
            String userId,
            String knowledgeBaseId,
            String fileName,
            String documentId,
            List<KnowledgeIndexChunk> chunks) {
        List<KnowledgeChunkEntity> entities = new ArrayList<>();
        if (chunks == null) {
            return entities;
        }
        for (KnowledgeIndexChunk chunk : chunks) {
            KnowledgeChunkEntity entity = new KnowledgeChunkEntity();
            entity.setChunkId(chunk.chunkId == null || chunk.chunkId.isEmpty() ? UUID.randomUUID().toString() : chunk.chunkId);
            entity.setDocumentId(documentId);
            entity.setKnowledgeBaseId(knowledgeBaseId);
            entity.setUserId(userId);
            entity.setChunkIndex(chunk.chunkIndex);
            entity.setFileName(fileName);
            entity.setContent(chunk.content == null ? "" : chunk.content);
            entity.setPageNo(asInt(chunk.metadata, "pageNo"));
            entity.setSectionTitle(asString(chunk.metadata, "sectionTitle"));
            entity.setContentDigest(asString(chunk.metadata, "contentDigest"));
            entity.setMetadataJson(toJson(chunk.metadata));
            entities.add(entity);
        }
        return entities;
    }

    private Integer asInt(Map<String, Object> metadata, String key) {
        if (metadata == null || metadata.get(key) == null) {
            return null;
        }
        return Integer.parseInt(String.valueOf(metadata.get(key)));
    }

    private String asString(Map<String, Object> metadata, String key) {
        if (metadata == null || metadata.get(key) == null) {
            return null;
        }
        String value = String.valueOf(metadata.get(key)).trim();
        return value.isEmpty() ? null : value;
    }

    private String toJson(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('"').append(escapeJson(entry.getKey())).append('"').append(':');
            Object value = entry.getValue();
            if (value == null) {
                builder.append("null");
            } else if (value instanceof Number || value instanceof Boolean) {
                builder.append(String.valueOf(value));
            } else {
                builder.append('"').append(escapeJson(String.valueOf(value))).append('"');
            }
        }
        builder.append('}');
        return builder.toString();
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String errorMessage(Exception ex) {
        if (ex.getMessage() != null && !ex.getMessage().trim().isEmpty()) {
            return ex.getMessage();
        }
        return ex.getClass().getSimpleName();
    }
}
