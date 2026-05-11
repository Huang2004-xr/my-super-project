import os
import re
from typing import Any, Dict

import httpx


class LlmError(RuntimeError):
    pass


class LlmClient:
    def __init__(self) -> None:
        self.provider = os.getenv("LLM_PROVIDER", "ollama").strip().lower()
        self.base_url = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434").strip().rstrip("/")
        self.model = os.getenv("OLLAMA_MODEL", "qwen3:4b").strip()
        self.timeout_seconds = float(os.getenv("OLLAMA_TIMEOUT_SECONDS", "60").strip() or "60")
        self.num_ctx = int(os.getenv("OLLAMA_NUM_CTX", "4096").strip() or "4096")

    def health(self) -> Dict[str, Any]:
        available = False
        error = None
        if self.provider == "ollama":
            try:
                available = self._model_available()
            except Exception as exc:  # noqa: BLE001 - health should report, not crash.
                error = str(exc)
        else:
            error = f"unsupported LLM provider: {self.provider}"
        result: Dict[str, Any] = {
            "provider": self.provider,
            "model": self.model,
            "baseUrl": self.base_url,
            "available": available,
        }
        if error:
            result["error"] = error
        return result

    def generate(self, capability: str, message: str, tool_context: Dict[str, Any]) -> str:
        if capability == "TEXT_CHAT":
            quick_answer = self._capability_question_answer(message)
            if quick_answer:
                return quick_answer
        system_prompt = self._system_prompt(capability)
        user_prompt = self._user_prompt(capability, message, tool_context)
        return self.chat(system_prompt, user_prompt)

    def summarize_memory(self, existing_summary: str, messages: list[dict[str, str]]) -> str:
        transcript = "\n".join(
            f"{item.get('role', 'UNKNOWN')}: {item.get('content', '')}" for item in messages if item.get("content")
        )
        if not transcript.strip():
            return existing_summary.strip()
        system_prompt = (
            "你是会话记忆总结器。请用简体中文压缩用户与助手的历史对话，"
            "只保留后续回答需要用到的事实、偏好、已讨论问题、用户目标和关键结论。"
            "不要记录日志、工具名、模型名、Trace 或无关实现细节。"
        )
        user_prompt = (
            f"已有记忆：\n{existing_summary or '无'}\n\n"
            f"需要合并进记忆的对话：\n{transcript}\n\n"
            "请输出 300 字以内的连续摘要。"
        )
        return self.chat(system_prompt, user_prompt)

    def chat(self, system_prompt: str, user_prompt: str) -> str:
        if self.provider != "ollama":
            raise LlmError(f"unsupported LLM provider: {self.provider}")
        if not self._model_available():
            raise LlmError(
                f"Ollama model '{self.model}' is not available at {self.base_url}. "
                "Please run 'ollama serve' and confirm 'ollama list'."
            )

        payload = {
            "model": self.model,
            "stream": False,
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt},
            ],
            "options": {"temperature": 0.4, "num_ctx": self.num_ctx},
        }
        try:
            with httpx.Client(timeout=self.timeout_seconds) as client:
                response = client.post(f"{self.base_url}/api/chat", json=payload)
                response.raise_for_status()
                data = response.json()
        except httpx.HTTPStatusError as exc:
            detail = exc.response.text.strip()
            raise LlmError(f"Ollama request failed: HTTP {exc.response.status_code}. {detail}") from exc
        except httpx.HTTPError as exc:
            raise LlmError(f"Ollama request failed: {exc}") from exc

        content = data.get("message", {}).get("content", "")
        content = self._strip_thinking(str(content)).strip()
        if not content:
            raise LlmError("Ollama returned an empty response")
        return content

    def _model_available(self) -> bool:
        with httpx.Client(timeout=min(self.timeout_seconds, 5.0)) as client:
            response = client.get(f"{self.base_url}/api/tags")
            response.raise_for_status()
            models = response.json().get("models", [])
        return any(item.get("name") == self.model for item in models)

    def _system_prompt(self, capability: str) -> str:
        common = (
            "你是超级Agent助手。请用简体中文回答，表达清晰、可执行、不要编造真实文件地址。"
            "不要使用 emoji 或特殊表情符号。"
            "如果能力是图片或视频创作，本地版本只输出文本方案，不声称已经生成真实媒体文件。"
            "不要暴露工具调用、Trace、日志、模型参数、Provider、JSON 调试内容或后端实现细节。"
        )
        prompts = {
            "TEXT_CHAT": "你负责文字沟通，直接回答用户问题。用户询问能力时只做简洁说明，不展开创作模板。",
            "VIDEO_CREATION": "你负责短视频创作，输出脚本、分镜、口播文案、节奏建议和下一步执行建议。",
            "IMAGE_CREATION": "你负责图片创作，输出可用于图像生成模型的提示词、画面构图、风格、色彩和负面提示词。",
            "KNOWLEDGE_RETRIEVAL": "你负责知识库问答，只能基于给定知识片段总结回答；片段不足时说明不足。",
        }
        return f"{common}\n{prompts.get(capability, prompts['TEXT_CHAT'])}"

    def _user_prompt(self, capability: str, message: str, tool_context: Dict[str, Any]) -> str:
        memory_context = self._memory_context(tool_context)
        if capability == "KNOWLEDGE_RETRIEVAL":
            hits = "\n".join(f"- {item}" for item in tool_context.get("hits", []))
            return f"{memory_context}用户问题：{message}\n\n命中的知识库片段：\n{hits or '- 无命中片段'}\n\n请给出回答。"

        image_note = ""
        if tool_context.get("referenceImage"):
            image_note = f"\n参考图片资产ID：{tool_context.get('referenceImage')}"

        if capability == "VIDEO_CREATION":
            return (
                f"用户目标：{message}{image_note}\n"
                f"{memory_context}"
                "请输出：1. 核心创意 2. 30秒脚本 3. 分镜表 4. 口播文案 5. 剪辑/发布建议。"
            )

        if capability == "IMAGE_CREATION":
            return (
                f"用户目标：{message}{image_note}\n"
                f"{memory_context}"
                "请输出：1. 中文设计说明 2. 可直接给图像模型使用的提示词 3. 风格/构图/色彩 4. 负面提示词。"
            )

        knowledge_note = "已启用内置知识库作为参考。" if tool_context.get("usedKnowledgeBase") else ""
        return (
            f"{memory_context}用户消息：{message}\n{image_note}\n{knowledge_note}\n"
            "请自然回复。若用户只是在问你是否具备某项能力，请用 1-3 句话直接说明当前能做什么和限制，"
            "不要生成图片提示词、视频脚本或执行方案。"
        )

    def _memory_context(self, tool_context: Dict[str, Any]) -> str:
        summary = str(tool_context.get("conversationMemory") or "").strip()
        recent_messages = tool_context.get("recentMessages") or []
        lines = []
        for item in recent_messages:
            if not isinstance(item, dict):
                continue
            role = str(item.get("role") or "UNKNOWN")
            content = str(item.get("content") or "").strip()
            if content:
                lines.append(f"{role}: {content}")
        if not summary and not lines:
            return ""
        recent = "\n".join(lines[-8:])
        return (
            "以下是当前对话的记忆上下文，请用于理解代词、前文问题和用户偏好，不要在回答中复述整段记忆：\n"
            f"长期摘要：{summary or '无'}\n"
            f"最近对话：\n{recent or '无'}\n\n"
        )

    def _strip_thinking(self, content: str) -> str:
        return re.sub(r"<think>.*?</think>", "", content, flags=re.DOTALL | re.IGNORECASE)

    def _capability_question_answer(self, message: str) -> str:
        asks_ability = any(term in message for term in ("可以", "能不能", "能否", "支持", "会不会", "你能", "你可以"))
        is_question = any(term in message for term in ("吗", "么", "？", "?"))
        if not asks_ability or not is_question:
            return ""
        if any(term in message for term in ("图片", "海报", "封面", "插画", "图像")):
            return "可以。我可以帮你生成图片创作方案和图片提示词；当前本地版本不会直接生成真实图片文件，但会给出可用于图片模型的提示词、风格和构图建议。"
        if any(term in message for term in ("视频", "短视频", "分镜", "脚本", "镜头")):
            return "可以。我可以帮你生成短视频脚本、分镜、口播文案和剪辑建议；当前本地版本不会直接生成真实视频文件。"
        if "知识库" in message:
            return "可以。你点击“引用知识库”后，我可以根据内置知识库内容回答问题。"
        return ""
