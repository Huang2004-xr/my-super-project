from datetime import datetime, timezone
from typing import Any, Dict, List, Optional
from uuid import uuid4

from pydantic import BaseModel, Field, field_validator


CAPABILITIES = {"TEXT_CHAT", "VIDEO_CREATION", "IMAGE_CREATION", "KNOWLEDGE_RETRIEVAL"}


class CapabilityDefinition(BaseModel):
    key: str
    name: str
    description: str
    examplePrompt: str


class CreateAgentRunRequest(BaseModel):
    message: str
    imageAssetId: Optional[str] = None
    useKnowledgeBase: bool = False
    knowledgeBaseId: Optional[str] = None
    capabilityHint: Optional[str] = None
    input: Dict[str, Any] = Field(default_factory=dict)

    @field_validator("message")
    @classmethod
    def message_required(cls, value: str) -> str:
        if not value or not value.strip():
            raise ValueError("message is required")
        return value.strip()

    @field_validator("capabilityHint")
    @classmethod
    def capability_hint_supported(cls, value: Optional[str]) -> Optional[str]:
        if value and value not in CAPABILITIES:
            raise ValueError(f"unsupported capabilityHint: {value}")
        return value


class MemoryMessage(BaseModel):
    role: str
    content: str


class MemorySummaryRequest(BaseModel):
    existingSummary: Optional[str] = None
    messages: List[MemoryMessage] = Field(default_factory=list)


class MemorySummaryResponse(BaseModel):
    summary: str


class ImageAsset(BaseModel):
    imageAssetId: str = Field(default_factory=lambda: str(uuid4()))
    fileName: str
    contentType: str


class AgentStep(BaseModel):
    stepId: str
    name: str
    status: str
    summary: str


class ToolCall(BaseModel):
    toolName: str
    input: Dict[str, Any] = Field(default_factory=dict)
    output: Dict[str, Any] = Field(default_factory=dict)
    status: str
    error: Optional[str] = None


class TraceEvent(BaseModel):
    eventType: str
    message: str
    timestamp: str


class Artifact(BaseModel):
    type: str
    title: str
    data: Dict[str, Any] = Field(default_factory=dict)


class AgentRun(BaseModel):
    runId: str = Field(default_factory=lambda: str(uuid4()))
    capability: str
    message: str
    imageAssetId: Optional[str] = None
    useKnowledgeBase: bool = False
    knowledgeBaseId: Optional[str] = None
    routeReason: str = ""
    status: str = "running"
    steps: List[AgentStep] = Field(default_factory=list)
    toolCalls: List[ToolCall] = Field(default_factory=list)
    traces: List[TraceEvent] = Field(default_factory=list)
    artifacts: List[Artifact] = Field(default_factory=list)
    finalResult: str = ""
    createdAt: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))
    updatedAt: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))


class KnowledgeIndexChunk(BaseModel):
    chunkId: str
    chunkIndex: int
    content: str
    metadata: Dict[str, Any] = Field(default_factory=dict)


class KnowledgeIndexResponse(BaseModel):
    userId: str
    knowledgeBaseId: str
    documentId: str
    fileName: str
    chunkCount: int
    status: str
    chunks: List[KnowledgeIndexChunk] = Field(default_factory=list)


class KnowledgeSearchRequest(BaseModel):
    userId: str
    knowledgeBaseId: str
    query: str
    topK: int = 4

    @field_validator("query")
    @classmethod
    def query_required(cls, value: str) -> str:
        if not value or not value.strip():
            raise ValueError("query is required")
        return value.strip()


class KnowledgeSearchHit(BaseModel):
    chunkId: str
    documentId: str
    chunkIndex: int
    content: str
    score: float
    fileName: Optional[str] = None
    pageNo: Optional[int] = None
    sectionTitle: Optional[str] = None
    metadata: Dict[str, Any] = Field(default_factory=dict)


class KnowledgeSearchResponse(BaseModel):
    userId: str
    knowledgeBaseId: str
    query: str
    hits: List[KnowledgeSearchHit] = Field(default_factory=list)
