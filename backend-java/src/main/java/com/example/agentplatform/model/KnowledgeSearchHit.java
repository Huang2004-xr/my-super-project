package com.example.agentplatform.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class KnowledgeSearchHit {
    public String chunkId;
    public String documentId;
    public int chunkIndex;
    public String content;
    public double score;
    public String fileName;
    public Integer pageNo;
    public String sectionTitle;
    public Map<String, Object> metadata = new LinkedHashMap<>();
}
