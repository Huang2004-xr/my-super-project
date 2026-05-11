package com.example.agentplatform.dto.admin;

import com.example.agentplatform.model.KnowledgeDocumentEntity;
import com.example.agentplatform.model.UserDto;
import java.util.List;
import java.util.Map;

public class AdminDashboardResponse {
    public AdminSummaryDto summary;
    public Map<String, Object> health;
    public List<AdminRunListItemDto> recentFailedRuns;
    public List<UserDto> recentUsers;
    public List<KnowledgeDocumentEntity> recentDocuments;
}