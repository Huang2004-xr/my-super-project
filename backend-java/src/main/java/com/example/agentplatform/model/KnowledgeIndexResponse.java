package com.example.agentplatform.model;

import java.util.ArrayList;
import java.util.List;

public class KnowledgeIndexResponse {
    public String userId;
    public String knowledgeBaseId;
    public String documentId;
    public String fileName;
    public int chunkCount;
    public String status;
    public List<KnowledgeIndexChunk> chunks = new ArrayList<>();
}
