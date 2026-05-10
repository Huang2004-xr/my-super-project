package com.example.agentplatform.service;

import com.example.agentplatform.model.AgentRun;
import com.example.agentplatform.model.AgentRunDetailEntity;
import com.example.agentplatform.model.AgentRunEntity;
import com.example.agentplatform.model.AgentStep;
import com.example.agentplatform.model.Artifact;
import com.example.agentplatform.model.ToolCall;
import com.example.agentplatform.model.TraceEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AgentRunRepository {
    private final AgentRunJpaRepository runRepository;
    private final AgentRunDetailRepository detailRepository;
    private final ObjectMapper objectMapper;

    public AgentRunRepository(AgentRunJpaRepository runRepository, AgentRunDetailRepository detailRepository,
            ObjectMapper objectMapper) {
        this.runRepository = runRepository;
        this.detailRepository = detailRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AgentRun save(AgentRun run, String userId, String conversationId) {
        AgentRunEntity entity = new AgentRunEntity();
        entity.setRunId(run.runId);
        entity.setUserId(userId);
        entity.setConversationId(conversationId);
        entity.setCapability(run.capability);
        entity.setMessage(run.message);
        entity.setImageAssetId(run.imageAssetId);
        entity.setUseKnowledgeBase(run.useKnowledgeBase);
        entity.setKnowledgeBaseId(run.knowledgeBaseId);
        entity.setRouteReason(run.routeReason);
        entity.setStatus(run.status);
        entity.setFinalResult(run.finalResult);
        entity.setCreatedAt(run.createdAt);
        entity.setUpdatedAt(run.updatedAt);
        runRepository.save(entity);

        AgentRunDetailEntity detail = new AgentRunDetailEntity();
        detail.setRunId(run.runId);
        detail.setStepsJson(writeJson(run.steps));
        detail.setToolCallsJson(writeJson(run.toolCalls));
        detail.setTracesJson(writeJson(run.traces));
        detail.setArtifactsJson(writeJson(run.artifacts));
        detailRepository.save(detail);
        return run;
    }

    public List<AgentRun> findAll(String userId) {
        List<AgentRun> runs = new ArrayList<>();
        for (AgentRunEntity entity : runRepository.findByUserIdOrderByCreatedAtDesc(userId)) {
            runs.add(toRun(entity));
        }
        return runs;
    }

    public Optional<AgentRun> findById(String userId, String runId) {
        return runRepository.findByRunIdAndUserId(runId, userId).map(this::toRun);
    }

    @Transactional
    public void updateTraces(String userId, String runId, List<TraceEvent> traces) {
        if (!runRepository.findByRunIdAndUserId(runId, userId).isPresent()) {
            return;
        }
        AgentRunDetailEntity detail = detailRepository.findById(runId).orElse(null);
        if (detail != null) {
            detail.setTracesJson(writeJson(traces));
            detailRepository.save(detail);
        }
    }

    private AgentRun toRun(AgentRunEntity entity) {
        AgentRun run = new AgentRun();
        run.runId = entity.getRunId();
        run.capability = entity.getCapability();
        run.message = entity.getMessage();
        run.imageAssetId = entity.getImageAssetId();
        run.useKnowledgeBase = entity.isUseKnowledgeBase();
        run.knowledgeBaseId = entity.getKnowledgeBaseId();
        run.routeReason = entity.getRouteReason();
        run.status = entity.getStatus();
        run.finalResult = entity.getFinalResult();
        run.createdAt = entity.getCreatedAt();
        run.updatedAt = entity.getUpdatedAt();
        AgentRunDetailEntity detail = detailRepository.findById(entity.getRunId()).orElse(null);
        if (detail != null) {
            run.steps = readJson(detail.getStepsJson(), new TypeReference<List<AgentStep>>() {});
            run.toolCalls = readJson(detail.getToolCallsJson(), new TypeReference<List<ToolCall>>() {});
            run.traces = readJson(detail.getTracesJson(), new TypeReference<List<TraceEvent>>() {});
            run.artifacts = readJson(detail.getArtifactsJson(), new TypeReference<List<Artifact>>() {});
        }
        return run;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize Agent Run detail", ex);
        }
    }

    private <T> T readJson(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json == null || json.trim().isEmpty() ? "[]" : json, type);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to deserialize Agent Run detail", ex);
        }
    }
}
