package com.example.agentplatform.model;

import java.util.ArrayList;
import java.util.List;

public class KnowledgeSearchResponse {
    public String userId;
    public String knowledgeBaseId;
    public String query;
    public List<KnowledgeSearchHit> hits = new ArrayList<>();
}
