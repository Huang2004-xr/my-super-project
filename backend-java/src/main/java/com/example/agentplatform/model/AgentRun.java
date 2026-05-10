package com.example.agentplatform.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class AgentRun {
    public String runId;
    public String capability;
    public String message;
    public String imageAssetId;
    public boolean useKnowledgeBase;
    public String knowledgeBaseId;
    public String routeReason;
    public String status;
    public List<AgentStep> steps = new ArrayList<>();
    public List<ToolCall> toolCalls = new ArrayList<>();
    public List<TraceEvent> traces = new ArrayList<>();
    public List<Artifact> artifacts = new ArrayList<>();
    public String finalResult;
    public Instant createdAt;
    public Instant updatedAt;
}
