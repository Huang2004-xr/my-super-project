package com.example.agentplatform.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "agent_run_details")
public class AgentRunDetailEntity {
    @Id
    @Column(name = "run_id", length = 64, nullable = false)
    private String runId;

    @Column(name = "steps_json", columnDefinition = "TEXT")
    private String stepsJson;

    @Column(name = "tool_calls_json", columnDefinition = "TEXT")
    private String toolCallsJson;

    @Column(name = "traces_json", columnDefinition = "TEXT")
    private String tracesJson;

    @Column(name = "artifacts_json", columnDefinition = "TEXT")
    private String artifactsJson;

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public String getStepsJson() { return stepsJson; }
    public void setStepsJson(String stepsJson) { this.stepsJson = stepsJson; }
    public String getToolCallsJson() { return toolCallsJson; }
    public void setToolCallsJson(String toolCallsJson) { this.toolCallsJson = toolCallsJson; }
    public String getTracesJson() { return tracesJson; }
    public void setTracesJson(String tracesJson) { this.tracesJson = tracesJson; }
    public String getArtifactsJson() { return artifactsJson; }
    public void setArtifactsJson(String artifactsJson) { this.artifactsJson = artifactsJson; }
}
