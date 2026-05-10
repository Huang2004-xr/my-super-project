package com.example.agentplatform.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "knowledge_chunks")
public class KnowledgeChunkEntity {
    @Id
    @Column(name = "chunk_id", length = 64, nullable = false)
    private String chunkId;

    @Column(name = "document_id", length = 64, nullable = false)
    private String documentId;

    @Column(name = "knowledge_base_id", length = 64, nullable = false)
    private String knowledgeBaseId;

    @Column(name = "user_id", length = 64, nullable = false)
    private String userId;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "page_no")
    private Integer pageNo;

    @Column(name = "section_title", length = 255)
    private String sectionTitle;

    @Column(name = "content_digest", length = 64)
    private String contentDigest;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    public String getChunkId() { return chunkId; }
    public void setChunkId(String chunkId) { this.chunkId = chunkId; }
    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }
    public String getKnowledgeBaseId() { return knowledgeBaseId; }
    public void setKnowledgeBaseId(String knowledgeBaseId) { this.knowledgeBaseId = knowledgeBaseId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public int getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(int chunkIndex) { this.chunkIndex = chunkIndex; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Integer getPageNo() { return pageNo; }
    public void setPageNo(Integer pageNo) { this.pageNo = pageNo; }
    public String getSectionTitle() { return sectionTitle; }
    public void setSectionTitle(String sectionTitle) { this.sectionTitle = sectionTitle; }
    public String getContentDigest() { return contentDigest; }
    public void setContentDigest(String contentDigest) { this.contentDigest = contentDigest; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
}
