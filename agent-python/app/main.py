import os
from typing import List

from fastapi import Depends, FastAPI, File, Form, Header, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware

from app.core.runtime import AgentRuntime
from app.core.knowledge_runtime import KnowledgeRuntime
from app.models.schemas import (
    AgentRun,
    CapabilityDefinition,
    CreateAgentRunRequest,
    ImageAsset,
    KnowledgeIndexResponse,
    KnowledgeSearchRequest,
    KnowledgeSearchResponse,
    MemorySummaryRequest,
    MemorySummaryResponse,
    TraceEvent,
)

app = FastAPI(title="Super Agent Runtime", version="0.2.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=[],
    allow_methods=["GET", "POST"],
    allow_headers=["*"],
)
runtime = AgentRuntime()
knowledge_runtime = KnowledgeRuntime()
INTERNAL_TOKEN = os.getenv("AGENT_INTERNAL_TOKEN", "local-dev-internal-token").strip()
MAX_IMAGE_BYTES = int(os.getenv("AGENT_MAX_IMAGE_BYTES", "10485760").strip() or "10485760")
MAX_DOCUMENT_BYTES = int(os.getenv("AGENT_MAX_DOCUMENT_BYTES", "20971520").strip() or "20971520")


def require_internal_token(x_internal_token: str | None = Header(default=None)) -> None:
    if not INTERNAL_TOKEN:
        raise HTTPException(status_code=500, detail="AGENT_INTERNAL_TOKEN is not configured")
    if x_internal_token != INTERNAL_TOKEN:
        raise HTTPException(status_code=401, detail="invalid internal token")


async def read_limited_upload(file: UploadFile, max_bytes: int, label: str) -> bytes:
    content = await file.read()
    if max_bytes > 0 and len(content) > max_bytes:
        raise HTTPException(status_code=413, detail=f"{label} file exceeds max size: {max_bytes} bytes")
    return content


@app.get("/health")
def health() -> dict:
    payload = runtime.health()
    payload["knowledge"] = knowledge_runtime.health()
    if not payload["knowledge"].get("available", False):
        payload["status"] = "degraded"
    return payload


@app.get("/agent/knowledge/health", dependencies=[Depends(require_internal_token)])
def knowledge_health() -> dict:
    return knowledge_runtime.health()


@app.post("/agent/knowledge/index", response_model=KnowledgeIndexResponse, dependencies=[Depends(require_internal_token)])
async def index_knowledge_document(
    userId: str = Form(...),
    knowledgeBaseId: str = Form(...),
    documentId: str = Form(...),
    file: UploadFile = File(...),
) -> KnowledgeIndexResponse:
    content = await read_limited_upload(file, MAX_DOCUMENT_BYTES, "document")
    return knowledge_runtime.index_document(
        user_id=userId,
        knowledge_base_id=knowledgeBaseId,
        document_id=documentId,
        file_name=file.filename or "document",
        content_type=file.content_type or "application/octet-stream",
        file_bytes=content,
    )


@app.post("/agent/knowledge/search", response_model=KnowledgeSearchResponse, dependencies=[Depends(require_internal_token)])
def search_knowledge(request: KnowledgeSearchRequest) -> KnowledgeSearchResponse:
    return knowledge_runtime.search(
        user_id=request.userId,
        knowledge_base_id=request.knowledgeBaseId,
        query=request.query,
        top_k=request.topK,
    )


@app.get("/agent/capabilities", response_model=List[CapabilityDefinition], dependencies=[Depends(require_internal_token)])
def capabilities() -> List[CapabilityDefinition]:
    return runtime.list_capabilities()


@app.post("/agent/assets/images", response_model=ImageAsset, dependencies=[Depends(require_internal_token)])
async def upload_image(file: UploadFile = File(...)) -> ImageAsset:
    if not file.content_type or not file.content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail="only image files are supported")
    # This prototype only keeps image metadata in memory.
    await read_limited_upload(file, MAX_IMAGE_BYTES, "image")
    return runtime.register_image(file.filename or "image", file.content_type)


@app.post("/agent/runs", response_model=AgentRun, dependencies=[Depends(require_internal_token)])
def create_run(request: CreateAgentRunRequest) -> AgentRun:
    return runtime.start_run(request)


@app.post("/agent/memory/summarize", response_model=MemorySummaryResponse, dependencies=[Depends(require_internal_token)])
def summarize_memory(request: MemorySummaryRequest) -> MemorySummaryResponse:
    summary = runtime.summarize_memory(request.existingSummary or "", request.messages)
    return MemorySummaryResponse(summary=summary)


@app.get("/agent/runs/{run_id}", response_model=AgentRun, dependencies=[Depends(require_internal_token)])
def get_run(run_id: str) -> AgentRun:
    try:
        return runtime.get_run(run_id)
    except KeyError as exc:
        raise HTTPException(status_code=404, detail="Agent Run not found") from exc


@app.get("/agent/traces/{run_id}", response_model=List[TraceEvent], dependencies=[Depends(require_internal_token)])
def traces(run_id: str) -> List[TraceEvent]:
    try:
        return runtime.get_run(run_id).traces
    except KeyError as exc:
        raise HTTPException(status_code=404, detail="Agent Run not found") from exc
