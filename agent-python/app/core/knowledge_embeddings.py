import hashlib
import math
import os
import re
from typing import Any, Dict, List

import httpx


class KnowledgeEmbeddingClient:
    def __init__(self) -> None:
        self.provider = os.getenv("KNOWLEDGE_EMBEDDING_PROVIDER", "hash").strip().lower()
        self.base_url = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434").strip().rstrip("/")
        self.model = os.getenv("KNOWLEDGE_EMBEDDING_MODEL", "nomic-embed-text").strip()
        self.dimension = int(os.getenv("KNOWLEDGE_VECTOR_DIM", "384").strip() or "384")
        self.timeout_seconds = float(os.getenv("KNOWLEDGE_EMBEDDING_TIMEOUT_SECONDS", "30").strip() or "30")

    def health(self) -> Dict[str, Any]:
        payload: Dict[str, Any] = {
            "provider": self.provider,
            "model": self.model,
            "dimension": self.dimension,
            "available": True,
        }
        if self.provider == "ollama":
            try:
                payload["available"] = self._model_available()
            except Exception as exc:  # noqa: BLE001
                payload["available"] = False
                payload["error"] = str(exc)
        return payload

    def embed_texts(self, texts: List[str]) -> List[List[float]]:
        normalized = [self._normalize_text(text) for text in texts]
        if self.provider == "ollama":
            return self._embed_with_ollama(normalized)
        return [self._hash_embed(text) for text in normalized]

    def _embed_with_ollama(self, texts: List[str]) -> List[List[float]]:
        if not self._model_available():
            raise RuntimeError(
                f"knowledge embedding model '{self.model}' is not available at {self.base_url}"
            )
        try:
            with httpx.Client(timeout=self.timeout_seconds) as client:
                response = client.post(
                    f"{self.base_url}/api/embed",
                    json={"model": self.model, "input": texts},
                )
                response.raise_for_status()
                data = response.json()
                embeddings = data.get("embeddings")
                if isinstance(embeddings, list) and embeddings:
                    return [self._normalize_vector(item) for item in embeddings]
        except httpx.HTTPError:
            pass

        result: List[List[float]] = []
        with httpx.Client(timeout=self.timeout_seconds) as client:
            for text in texts:
                response = client.post(
                    f"{self.base_url}/api/embeddings",
                    json={"model": self.model, "prompt": text},
                )
                response.raise_for_status()
                data = response.json()
                result.append(self._normalize_vector(data.get("embedding") or []))
        return result

    def _model_available(self) -> bool:
        with httpx.Client(timeout=min(self.timeout_seconds, 5.0)) as client:
            response = client.get(f"{self.base_url}/api/tags")
            response.raise_for_status()
            models = response.json().get("models", [])
        return any(item.get("name") == self.model for item in models)

    def _hash_embed(self, text: str) -> List[float]:
        vector = [0.0] * self.dimension
        tokens = re.findall(r"[\u4e00-\u9fff]|[A-Za-z0-9_]+", text.lower())
        if not tokens:
            return vector
        for token in tokens:
            index_hash = hashlib.sha256(token.encode("utf-8")).digest()
            sign_hash = hashlib.md5(token.encode("utf-8")).digest()
            index = int.from_bytes(index_hash[:4], "big") % self.dimension
            sign = 1.0 if sign_hash[0] % 2 == 0 else -1.0
            vector[index] += sign
        return self._normalize_vector(vector)

    def _normalize_vector(self, values: List[float]) -> List[float]:
        vector = [float(value) for value in values[: self.dimension]]
        if len(vector) < self.dimension:
            vector.extend([0.0] * (self.dimension - len(vector)))
        length = math.sqrt(sum(value * value for value in vector))
        if length <= 0:
            return vector
        return [value / length for value in vector]

    def _normalize_text(self, value: str) -> str:
        return re.sub(r"\s+", " ", value or "").strip()
