package com.example.agentplatform.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class KnowledgeIndexChunk {
    public String chunkId;
    public int chunkIndex;
    public String content;
    public Map<String, Object> metadata = new LinkedHashMap<>();
}
