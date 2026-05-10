package com.example.agentplatform.service;

import com.example.agentplatform.dto.CreateAgentRunRequest;
import com.example.agentplatform.model.AgentRun;
import com.example.agentplatform.model.CapabilityDefinition;
import com.example.agentplatform.model.ImageAsset;
import com.example.agentplatform.model.KnowledgeIndexResponse;
import com.example.agentplatform.model.KnowledgeSearchResponse;
import com.example.agentplatform.model.TraceEvent;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class AgentServiceClient {
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final RestTemplate restTemplate;
    private final String serviceUrl;
    private final String internalToken;

    public AgentServiceClient(RestTemplate restTemplate,
            @Value("${agent.service-url}") String serviceUrl,
            @Value("${agent.internal-token}") String internalToken) {
        this.restTemplate = restTemplate;
        this.serviceUrl = serviceUrl;
        this.internalToken = internalToken;
    }

    public Map<String, Object> health() {
        try {
            return restTemplate.getForObject(serviceUrl + "/health", Map.class);
        } catch (RestClientException ex) {
            throw pythonServiceException("检查 Python Agent 健康状态", ex);
        }
    }

    public List<CapabilityDefinition> getCapabilities() {
        try {
            ResponseEntity<CapabilityDefinition[]> response = restTemplate.exchange(
                    serviceUrl + "/agent/capabilities",
                    HttpMethod.GET,
                    internalEntity(),
                    CapabilityDefinition[].class);
            CapabilityDefinition[] body = response.getBody();
            return body == null ? Collections.emptyList() : Arrays.asList(body);
        } catch (RestClientException ex) {
            throw pythonServiceException("查询 Python Agent 能力列表", ex);
        }
    }

    public AgentRun startRun(CreateAgentRunRequest request) {
        try {
            ResponseEntity<AgentRun> response = restTemplate.exchange(
                    serviceUrl + "/agent/runs",
                    HttpMethod.POST,
                    internalEntity(request),
                    AgentRun.class);
            return response.getBody();
        } catch (RestClientException ex) {
            throw pythonServiceException("执行 Python Agent Run", ex);
        }
    }

    public String summarizeMemory(String existingSummary, List<Map<String, String>> messages) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("existingSummary", existingSummary);
            body.put("messages", messages);
            ResponseEntity<Map> response = restTemplate.exchange(
                    serviceUrl + "/agent/memory/summarize",
                    HttpMethod.POST,
                    internalEntity(body),
                    Map.class);
            Object summary = response.getBody() == null ? null : response.getBody().get("summary");
            return summary == null ? "" : String.valueOf(summary);
        } catch (RestClientException ex) {
            throw pythonServiceException("总结会话记忆", ex);
        }
    }

    public AgentRun getRun(String runId) {
        try {
            ResponseEntity<AgentRun> response = restTemplate.exchange(
                    url("/agent/runs/" + runId),
                    HttpMethod.GET,
                    internalEntity(),
                    AgentRun.class);
            return response.getBody();
        } catch (RestClientException ex) {
            throw pythonServiceException("查询 Python Agent Run", ex);
        }
    }

    public List<TraceEvent> getTraces(String runId) {
        try {
            ResponseEntity<List<TraceEvent>> response = restTemplate.exchange(
                    url("/agent/traces/" + runId),
                    HttpMethod.GET,
                    internalEntity(),
                    new ParameterizedTypeReference<List<TraceEvent>>() {});
            return response.getBody() == null ? Collections.emptyList() : response.getBody();
        } catch (RestClientException ex) {
            throw pythonServiceException("查询 Python Agent Trace", ex);
        }
    }

    public ImageAsset uploadImage(MultipartFile file) throws IOException {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            addInternalToken(headers);
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };
            body.add("file", resource);
            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
            return restTemplate.postForObject(serviceUrl + "/agent/assets/images", request, ImageAsset.class);
        } catch (RestClientException ex) {
            throw pythonServiceException("上传图片到 Python Agent", ex);
        }
    }

    public KnowledgeIndexResponse indexKnowledgeDocument(
            String userId,
            String knowledgeBaseId,
            String documentId,
            MultipartFile file) throws IOException {
        return indexKnowledgeDocument(
                userId,
                knowledgeBaseId,
                documentId,
                file.getOriginalFilename(),
                file.getBytes());
    }

    public KnowledgeIndexResponse indexKnowledgeDocument(
            String userId,
            String knowledgeBaseId,
            String documentId,
            String fileName,
            byte[] fileContent) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            addInternalToken(headers);
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            ByteArrayResource resource = new ByteArrayResource(fileContent) {
                @Override
                public String getFilename() {
                    return fileName;
                }
            };
            body.add("userId", userId);
            body.add("knowledgeBaseId", knowledgeBaseId);
            body.add("documentId", documentId);
            body.add("file", resource);
            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
            return restTemplate.postForObject(serviceUrl + "/agent/knowledge/index", request, KnowledgeIndexResponse.class);
        } catch (RestClientException ex) {
            throw pythonServiceException("为知识库索引文档", ex);
        }
    }

    public KnowledgeSearchResponse searchKnowledge(String userId, String knowledgeBaseId, String query, int topK) {
        try {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("userId", userId);
            request.put("knowledgeBaseId", knowledgeBaseId);
            request.put("query", query);
            request.put("topK", topK);
            ResponseEntity<KnowledgeSearchResponse> response = restTemplate.exchange(
                    serviceUrl + "/agent/knowledge/search",
                    HttpMethod.POST,
                    internalEntity(request),
                    KnowledgeSearchResponse.class);
            return response.getBody();
        } catch (RestClientException ex) {
            throw pythonServiceException("搜索知识库", ex);
        }
    }

    private HttpEntity<Void> internalEntity() {
        HttpHeaders headers = new HttpHeaders();
        addInternalToken(headers);
        return new HttpEntity<>(headers);
    }

    private <T> HttpEntity<T> internalEntity(T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        addInternalToken(headers);
        return new HttpEntity<>(body, headers);
    }

    private void addInternalToken(HttpHeaders headers) {
        if (internalToken == null || internalToken.trim().isEmpty()) {
            throw new IllegalStateException("agent.internal-token is not configured");
        }
        headers.set(INTERNAL_TOKEN_HEADER, internalToken);
    }

    private String url(String path) {
        return UriComponentsBuilder.fromHttpUrl(serviceUrl).path(path).build().encode().toUriString();
    }

    private IllegalStateException pythonServiceException(String action, RestClientException ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        if (message.contains("Milvus") || message.contains("vectorStore") || message.contains("knowledge")) {
            return new IllegalStateException("知识库向量服务不可用，请先检查独立 Milvus 服务和 Python Agent health；action="
                    + action + "; detail=" + message, ex);
        }
        if (message.contains("Tesseract OCR is not installed") || message.contains("TesseractNotFoundError")) {
            return new IllegalStateException("图片 OCR 不可用，请先安装 Tesseract 并加入 PATH；action="
                    + action + "; detail=" + message, ex);
        }
        if (message.contains("scanned PDF OCR fallback") || message.contains("pdf text extraction returned empty result")) {
            return new IllegalStateException("当前原型暂不支持扫描版 PDF 自动 OCR，请先上传可复制文本的 PDF，或先转成图片/文本后再上传；action="
                    + action + "; detail=" + message, ex);
        }
        if (message.contains("Read timed out") || message.contains("SocketTimeoutException")) {
            return new IllegalStateException("Python Agent 执行超时，请稍后重试或检查本地 Ollama 模型是否仍在生成；action="
                    + action + "; detail=" + message, ex);
        }
        if (message.contains("Connection refused")) {
            return new IllegalStateException("Python Agent 服务未启动，请先启动 agent-python 的 uvicorn 服务；action="
                    + action + "; detail=" + message, ex);
        }
        return new IllegalStateException("Python Agent 调用失败；action=" + action + "; detail=" + message, ex);
    }
}
