import os
from typing import Any, Dict, List

from pymilvus import Collection, CollectionSchema, DataType, FieldSchema, connections, utility


class KnowledgeMilvusStore:
    def __init__(self) -> None:
        self.alias = "knowledge"
        self.uri = os.getenv("KNOWLEDGE_MILVUS_URI", "").strip()
        self.host = os.getenv("KNOWLEDGE_MILVUS_HOST", "127.0.0.1").strip() or "127.0.0.1"
        self.port = os.getenv("KNOWLEDGE_MILVUS_PORT", "19530").strip() or "19530"
        self.user = os.getenv("KNOWLEDGE_MILVUS_USER", "").strip()
        self.password = os.getenv("KNOWLEDGE_MILVUS_PASSWORD", "").strip()
        self.collection_name = os.getenv("KNOWLEDGE_MILVUS_COLLECTION", "knowledge_chunks").strip() or "knowledge_chunks"
        self.dimension = int(os.getenv("KNOWLEDGE_VECTOR_DIM", "384").strip() or "384")
        self.metric_type = os.getenv("KNOWLEDGE_MILVUS_METRIC_TYPE", "COSINE").strip() or "COSINE"
        self.index_type = os.getenv("KNOWLEDGE_MILVUS_INDEX_TYPE", "IVF_FLAT").strip() or "IVF_FLAT"
        self.nlist = int(os.getenv("KNOWLEDGE_MILVUS_INDEX_NLIST", "128").strip() or "128")
        self.nprobe = int(os.getenv("KNOWLEDGE_MILVUS_SEARCH_NPROBE", "10").strip() or "10")
        self._connected = False

    def health(self) -> Dict[str, Any]:
        payload = {
            "type": "milvus",
            "collection": self.collection_name,
            "available": False,
        }
        try:
            collection = self._ensure_collection(load=False)
            payload["available"] = True
            payload["loaded"] = collection.name in utility.list_collections(using=self.alias)
        except Exception as exc:  # noqa: BLE001
            payload["error"] = str(exc)
        return payload

    def replace_document_chunks(self, rows: List[Dict[str, Any]], user_id: str, knowledge_base_id: str, document_id: str) -> None:
        collection = self._ensure_collection(load=True)
        expr = (
            f'user_id == "{self._escape(user_id)}" '
            f'and knowledge_base_id == "{self._escape(knowledge_base_id)}" '
            f'and document_id == "{self._escape(document_id)}"'
        )
        collection.delete(expr)
        if not rows:
            collection.flush()
            return
        data = [
            [row["chunk_id"] for row in rows],
            [row["user_id"] for row in rows],
            [row["knowledge_base_id"] for row in rows],
            [row["document_id"] for row in rows],
            [int(row["chunk_index"]) for row in rows],
            [str(row.get("file_name") or "")[:255] for row in rows],
            [str(row.get("section_title") or "")[:255] for row in rows],
            [int(row.get("page_no") or 0) for row in rows],
            [str(row.get("content") or "")[:8192] for row in rows],
            [row["vector"] for row in rows],
        ]
        collection.insert(data)
        collection.flush()

    def search(self, user_id: str, knowledge_base_id: str, vector: List[float], top_k: int) -> List[Dict[str, Any]]:
        collection = self._ensure_collection(load=True)
        expr = (
            f'user_id == "{self._escape(user_id)}" '
            f'and knowledge_base_id == "{self._escape(knowledge_base_id)}"'
        )
        result = collection.search(
            data=[vector],
            anns_field="vector",
            param={"metric_type": self.metric_type, "params": {"nprobe": self.nprobe}},
            limit=max(1, min(top_k, 20)),
            expr=expr,
            output_fields=["document_id", "chunk_index", "file_name", "section_title", "page_no", "content"],
        )
        hits: List[Dict[str, Any]] = []
        for item in result[0]:
            entity = item.entity
            hits.append({
                "chunkId": item.id,
                "documentId": entity.get("document_id"),
                "chunkIndex": int(entity.get("chunk_index") or 0),
                "fileName": entity.get("file_name") or None,
                "sectionTitle": entity.get("section_title") or None,
                "pageNo": int(entity.get("page_no") or 0),
                "content": entity.get("content") or "",
                "score": float(item.distance),
            })
        return hits

    def _ensure_collection(self, load: bool) -> Collection:
        self._connect()
        if not utility.has_collection(self.collection_name, using=self.alias):
            schema = CollectionSchema(
                fields=[
                    FieldSchema(name="chunk_id", dtype=DataType.VARCHAR, is_primary=True, max_length=64),
                    FieldSchema(name="user_id", dtype=DataType.VARCHAR, max_length=64),
                    FieldSchema(name="knowledge_base_id", dtype=DataType.VARCHAR, max_length=64),
                    FieldSchema(name="document_id", dtype=DataType.VARCHAR, max_length=64),
                    FieldSchema(name="chunk_index", dtype=DataType.INT64),
                    FieldSchema(name="file_name", dtype=DataType.VARCHAR, max_length=255),
                    FieldSchema(name="section_title", dtype=DataType.VARCHAR, max_length=255),
                    FieldSchema(name="page_no", dtype=DataType.INT64),
                    FieldSchema(name="content", dtype=DataType.VARCHAR, max_length=8192),
                    FieldSchema(name="vector", dtype=DataType.FLOAT_VECTOR, dim=self.dimension),
                ],
                description="Knowledge base chunks",
            )
            collection = Collection(name=self.collection_name, schema=schema, using=self.alias)
            collection.create_index(
                field_name="vector",
                index_params={
                    "metric_type": self.metric_type,
                    "index_type": self.index_type,
                    "params": {"nlist": self.nlist},
                },
            )
        else:
            collection = Collection(name=self.collection_name, using=self.alias)
        if load:
            collection.load()
        return collection

    def _connect(self) -> None:
        if self._connected:
            return
        params: Dict[str, Any] = {"alias": self.alias}
        if self.uri:
            params["uri"] = self.uri
        else:
            params["host"] = self.host
            params["port"] = self.port
        if self.user:
            params["user"] = self.user
        if self.password:
            params["password"] = self.password
        connections.connect(**params)
        self._connected = True

    def _escape(self, value: str) -> str:
        return value.replace("\\", "\\\\").replace('"', '\\"')
