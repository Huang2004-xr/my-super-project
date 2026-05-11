import hashlib
from typing import Any, Dict, List
from uuid import uuid4

from app.core.knowledge_embeddings import KnowledgeEmbeddingClient
from app.core.knowledge_milvus import KnowledgeMilvusStore
from app.core.knowledge_parser import KnowledgeParser
from app.models.schemas import (
    KnowledgeIndexChunk,
    KnowledgeIndexResponse,
    KnowledgeSearchHit,
    KnowledgeSearchResponse,
)


class KnowledgeRuntime:
    def __init__(self) -> None:
        self.parser = KnowledgeParser()
        self.embedder = KnowledgeEmbeddingClient()
        self.store = KnowledgeMilvusStore()

    def health(self) -> Dict[str, Any]:
        embedding = self.embedder.health()
        vector_store = self.store.health()
        available = bool(embedding.get("available", True)) and bool(vector_store.get("available", False))
        return {
            "status": "ok" if available else "degraded",
            "available": available,
            "embedding": embedding,
            "vectorStore": vector_store,
        }

    def index_document(
        self,
        user_id: str,
        knowledge_base_id: str,
        document_id: str,
        file_name: str,
        content_type: str,
        file_bytes: bytes,
    ) -> KnowledgeIndexResponse:
        parsed_chunks = self.parser.parse(file_name=file_name, content_type=content_type, file_bytes=file_bytes)
        vectors = self.embedder.embed_texts([item["content"] for item in parsed_chunks])
        rows: List[Dict[str, Any]] = []
        response_chunks: List[KnowledgeIndexChunk] = []
        for index, (chunk, vector) in enumerate(zip(parsed_chunks, vectors)):
            metadata = dict(chunk.get("metadata") or {})
            chunk_id = str(uuid4())
            row = {
                "chunk_id": chunk_id,
                "user_id": user_id,
                "knowledge_base_id": knowledge_base_id,
                "document_id": document_id,
                "chunk_index": index,
                "file_name": file_name,
                "section_title": str(metadata.get("sectionTitle") or "")[:255],
                "page_no": int(metadata.get("pageNo") or 0),
                "content": str(chunk.get("content") or ""),
                "vector": vector,
            }
            rows.append(row)
            response_chunks.append(
                KnowledgeIndexChunk(
                    chunkId=chunk_id,
                    chunkIndex=index,
                    content=row["content"],
                    metadata={
                        **metadata,
                        "contentDigest": self._digest(row["content"]),
                    },
                )
            )
        self.store.replace_document_chunks(rows, user_id=user_id, knowledge_base_id=knowledge_base_id, document_id=document_id)
        return KnowledgeIndexResponse(
            userId=user_id,
            knowledgeBaseId=knowledge_base_id,
            documentId=document_id,
            fileName=file_name,
            chunkCount=len(response_chunks),
            status="INDEXED",
            chunks=response_chunks,
        )

    def search(self, user_id: str, knowledge_base_id: str, query: str, top_k: int) -> KnowledgeSearchResponse:
        vector = self.embedder.embed_texts([query])[0]
        raw_hits = self.store.search(user_id=user_id, knowledge_base_id=knowledge_base_id, vector=vector, top_k=top_k)
        hits = [
            KnowledgeSearchHit(
                chunkId=str(item.get("chunkId") or ""),
                documentId=str(item.get("documentId") or ""),
                chunkIndex=int(item.get("chunkIndex") or 0),
                content=str(item.get("content") or ""),
                score=float(item.get("score") or 0.0),
                fileName=item.get("fileName") or None,
                pageNo=(int(item.get("pageNo")) if int(item.get("pageNo") or 0) > 0 else None),
                sectionTitle=item.get("sectionTitle") or None,
                metadata={
                    "source": "milvus",
                    "contentDigest": self._digest(str(item.get("content") or "")),
                },
            )
            for item in raw_hits
        ]
        return KnowledgeSearchResponse(
            userId=user_id,
            knowledgeBaseId=knowledge_base_id,
            query=query,
            hits=hits,
        )

    def _digest(self, value: str) -> str:
        return hashlib.md5(value.encode("utf-8")).hexdigest()
