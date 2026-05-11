from typing import Any, Dict, List

from app.models.schemas import Artifact, ToolCall


class ToolRegistry:
    builtin_knowledge = [
        "超级Agent支持文字沟通、视频创作、图片创作和知识库检索四类能力。",
        "视频创作当前输出短视频脚本、分镜结构、口播文案和执行建议，不生成真实视频文件。",
        "图片创作当前输出图片生成提示词、风格建议、构图说明和负面提示词，不生成真实图片文件。",
        "知识库检索会引用内置知识库，通过对话形式返回命中的知识片段和总结回答。",
        "前端只调用Java后端，Java负责用户、会话、图库和数据库持久化，Python负责Agent Runtime和LLM调用。",
    ]

    def call(self, name: str, payload: Dict[str, Any]) -> Dict[str, Any]:
        if name == "chat.respond":
            return self._chat(payload)
        if name == "media.plan_video":
            return self._video(payload)
        if name == "media.generate_image":
            return self._image(payload)
        if name == "knowledge.search_builtin":
            return self._knowledge(payload)
        raise ValueError(f"Unknown tool: {name}")

    def execute_tool(self, name: str, payload: Dict[str, Any], artifact_type: str, artifact_title: str) -> tuple[ToolCall, Artifact]:
        output = self.call(name, payload)
        return (
            ToolCall(toolName=name, input=payload, output=output, status="success"),
            Artifact(type=artifact_type, title=artifact_title, data=output),
        )

    def _chat(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        return {
            "message": payload["message"],
            "usedKnowledgeBase": payload.get("useKnowledgeBase", False),
            "referenceImage": payload.get("imageAssetId"),
            "context": "已准备文字沟通上下文，最终回复由本地Ollama模型生成。",
        }

    def _video(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        return {
            "goal": payload["message"],
            "suggestedStructure": ["开场钩子", "痛点描述", "价值展示", "行动引导"],
            "videoTaskId": "video-task-text-plan",
            "referenceImage": payload.get("imageAssetId"),
            "context": "已准备视频创作上下文，最终脚本和分镜由本地Ollama模型生成。",
        }

    def _image(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        return {
            "goal": payload["message"],
            "suggestedStyle": "商业海报 / 科技感 / 高清细节",
            "imageTaskId": "image-task-text-plan",
            "referenceImage": payload.get("imageAssetId"),
            "context": "已准备图片创作上下文，最终提示词由本地Ollama模型生成。",
        }

    def _knowledge(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        query = payload["message"]
        hits = self._normalize_hits(payload.get("knowledgeHits"))
        if not hits:
            hits = self._search(query)
        return {
            "query": query,
            "hits": hits,
            "knowledgeBaseId": payload.get("knowledgeBaseId") or "builtin",
            "context": "已检索内置知识库，最终回答由本地Ollama模型基于命中片段生成。",
        }

    def _normalize_hits(self, hits: Any) -> List[str]:
        if not isinstance(hits, list):
            return []
        normalized: List[str] = []
        for item in hits:
            if isinstance(item, str):
                value = item.strip()
                if value:
                    normalized.append(value)
                continue
            if not isinstance(item, dict):
                continue
            content = str(item.get("content") or "").strip()
            if not content:
                continue
            source_parts = []
            file_name = str(item.get("fileName") or "").strip()
            if file_name:
                source_parts.append(file_name)
            page_no = item.get("pageNo")
            if isinstance(page_no, int) and page_no > 0:
                source_parts.append(f"第{page_no}页")
            section_title = str(item.get("sectionTitle") or "").strip()
            if section_title:
                source_parts.append(section_title)
            prefix = f"[{ ' / '.join(source_parts) }] " if source_parts else ""
            normalized.append(prefix + content)
        return normalized

    def _search(self, query: str) -> List[str]:
        terms = [item for item in ("超级Agent", "文字", "视频", "图片", "知识库", "Java", "Python", "前端") if item in query]
        if not terms:
            return self.builtin_knowledge[:4]
        hits = [item for item in self.builtin_knowledge if any(term in item for term in terms)]
        return hits or self.builtin_knowledge[:4]
