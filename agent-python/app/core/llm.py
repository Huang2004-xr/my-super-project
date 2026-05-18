import os
import re
from typing import Any, Dict, Optional

import httpx


class LlmError(RuntimeError):
    def __init__(self, message: str, code: str = "UNKNOWN", retryable: bool = False, status_code: Optional[int] = None) -> None:
        super().__init__(message)
        self.code = code
        self.retryable = retryable
        self.status_code = status_code


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

    def generate(
        self,
        capability: str,
        message: str,
        tool_context: Dict[str, Any],
        provider_config: Optional[Dict[str, Any]] = None,
    ) -> str:
        if capability == "TEXT_CHAT":
            local_answer = self._local_intent_answer(message, tool_context, provider_config)
            if local_answer:
                return local_answer
        system_prompt = self._system_prompt(capability)
        user_prompt = self._user_prompt(capability, message, tool_context)
        return self.chat(system_prompt, user_prompt, provider_config)

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

    def chat(self, system_prompt: str, user_prompt: str, provider_config: Optional[Dict[str, Any]] = None) -> str:
        if provider_config:
            return self._chat_external(system_prompt, user_prompt, provider_config)
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

    def _chat_external(self, system_prompt: str, user_prompt: str, provider_config: Dict[str, Any]) -> str:
        attempts = [provider_config]
        if provider_config.get("enableFallback"):
            fallbacks = provider_config.get("fallbackProviders")
            if isinstance(fallbacks, list):
                attempts.extend(item for item in fallbacks if isinstance(item, dict))

        errors = []
        for index, attempt in enumerate(attempts):
            try:
                return self._chat_external_single(system_prompt, user_prompt, attempt)
            except LlmError as exc:
                errors.append(f"{attempt.get('name') or 'external'}: [{exc.code}] {exc}")
                if index == 0 and not provider_config.get("enableFallback"):
                    raise
                if not exc.retryable:
                    raise
        raise LlmError("all configured external providers failed: " + " | ".join(errors))

    def _chat_external_single(self, system_prompt: str, user_prompt: str, provider_config: Dict[str, Any]) -> str:
        api_format = str(provider_config.get("apiFormat") or "openai_chat_completions").strip()
        base_url = str(provider_config.get("baseUrl") or "").strip().rstrip("/")
        api_key = str(provider_config.get("apiKey") or "").strip()
        model = str(provider_config.get("model") or "").strip()
        auth_header_name = str(provider_config.get("authHeaderName") or "Authorization").strip()
        if not base_url:
            raise LlmError("external provider baseUrl is required")
        if not api_key:
            raise LlmError("external provider apiKey is required")
        if not model:
            raise LlmError("external provider model is required")

        if api_format == "openai_chat_completions":
            return self._chat_openai_chat_completions(system_prompt, user_prompt, base_url, api_key, auth_header_name, model)
        if api_format == "anthropic_messages":
            return self._chat_anthropic_messages(system_prompt, user_prompt, base_url, api_key, auth_header_name, model)
        if api_format == "openai_responses":
            return self._chat_openai_responses(system_prompt, user_prompt, base_url, api_key, auth_header_name, model)
        raise LlmError(f"unsupported external API format: {api_format}", "PROTOCOL_MISMATCH")

    def _chat_openai_chat_completions(
        self,
        system_prompt: str,
        user_prompt: str,
        base_url: str,
        api_key: str,
        auth_header_name: str,
        model: str,
    ) -> str:
        headers = {"Content-Type": "application/json"}
        self._apply_external_auth(headers, auth_header_name, api_key)
        payload = {
            "model": model,
            "stream": False,
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt},
            ],
            "temperature": 0.4,
        }
        try:
            with httpx.Client(timeout=self.timeout_seconds) as client:
                response = client.post(self._external_chat_url(base_url), json=payload, headers=headers)
                response.raise_for_status()
                data = response.json()
        except httpx.HTTPStatusError as exc:
            raise self._external_http_error(exc) from exc
        except httpx.HTTPError as exc:
            raise self._external_transport_error(exc) from exc

        content = ""
        choices = data.get("choices") if isinstance(data, dict) else None
        if choices:
            message = choices[0].get("message", {})
            content = message.get("content") or choices[0].get("text") or ""
        content = self._strip_thinking(str(content)).strip()
        if not content:
            raise LlmError("External provider returned an empty response")
        return content

    def _chat_anthropic_messages(
        self,
        system_prompt: str,
        user_prompt: str,
        base_url: str,
        api_key: str,
        auth_header_name: str,
        model: str,
    ) -> str:
        headers = {"Content-Type": "application/json", "anthropic-version": "2023-06-01"}
        self._apply_external_auth(headers, auth_header_name or "x-api-key", api_key)
        payload = {
            "model": model,
            "system": system_prompt,
            "messages": [{"role": "user", "content": user_prompt}],
            "max_tokens": 1200,
            "temperature": 0.4,
        }
        try:
            with httpx.Client(timeout=self.timeout_seconds) as client:
                response = client.post(self._anthropic_messages_url(base_url), json=payload, headers=headers)
                response.raise_for_status()
                data = response.json()
        except httpx.HTTPStatusError as exc:
            raise self._external_http_error(exc) from exc
        except httpx.HTTPError as exc:
            raise self._external_transport_error(exc) from exc

        content_items = data.get("content") if isinstance(data, dict) else None
        content = ""
        if isinstance(content_items, list):
            parts = []
            for item in content_items:
                if isinstance(item, dict) and item.get("type") in (None, "text"):
                    parts.append(str(item.get("text") or ""))
            content = "\n".join(part for part in parts if part.strip())
        content = self._strip_thinking(str(content)).strip()
        if not content:
            raise LlmError("External provider returned an empty response")
        return content

    def _chat_openai_responses(
        self,
        system_prompt: str,
        user_prompt: str,
        base_url: str,
        api_key: str,
        auth_header_name: str,
        model: str,
    ) -> str:
        headers = {"Content-Type": "application/json"}
        self._apply_external_auth(headers, auth_header_name, api_key)
        payload = {
            "model": model,
            "input": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt},
            ],
            "max_output_tokens": 1200,
        }
        try:
            with httpx.Client(timeout=self.timeout_seconds) as client:
                response = client.post(self._responses_url(base_url), json=payload, headers=headers)
                response.raise_for_status()
                data = response.json()
        except httpx.HTTPStatusError as exc:
            raise self._external_http_error(exc) from exc
        except httpx.HTTPError as exc:
            raise self._external_transport_error(exc) from exc

        content = str(data.get("output_text") or "").strip() if isinstance(data, dict) else ""
        if not content and isinstance(data, dict):
            content = self._extract_responses_text(data)
        content = self._strip_thinking(content).strip()
        if not content:
            raise LlmError("External provider returned an empty response")
        return content

    def _apply_external_auth(self, headers: Dict[str, str], auth_header_name: str, api_key: str) -> None:
        header = (auth_header_name or "Authorization").strip()
        if header.lower() == "authorization":
            headers["Authorization"] = api_key if api_key.lower().startswith("bearer ") else f"Bearer {api_key}"
        else:
            headers[header] = api_key

    def _external_chat_url(self, base_url: str) -> str:
        if base_url.endswith("/chat/completions"):
            return base_url
        if base_url.endswith("/v1"):
            return f"{base_url}/chat/completions"
        return f"{base_url}/v1/chat/completions"

    def _anthropic_messages_url(self, base_url: str) -> str:
        if base_url.endswith("/messages"):
            return base_url
        if base_url.endswith("/v1"):
            return f"{base_url}/messages"
        return f"{base_url}/v1/messages"

    def _responses_url(self, base_url: str) -> str:
        if base_url.endswith("/responses"):
            return base_url
        if base_url.endswith("/v1"):
            return f"{base_url}/responses"
        return f"{base_url}/v1/responses"

    def _external_http_error(self, exc: httpx.HTTPStatusError) -> LlmError:
        status = exc.response.status_code
        detail = self._redact_secret(exc.response.text.strip())
        code = self._classify_external_status(status, detail)
        retryable = status in (408, 409, 429, 500, 502, 503, 504, 529)
        return LlmError(f"External provider request failed: HTTP {status}. {detail}", code, retryable, status)

    def _external_transport_error(self, exc: httpx.HTTPError) -> LlmError:
        code = "TIMEOUT" if isinstance(exc, (httpx.TimeoutException, httpx.ConnectTimeout, httpx.ReadTimeout)) else "NETWORK"
        return LlmError(f"External provider request failed: {exc}", code, True)

    def _classify_external_status(self, status: int, detail: str) -> str:
        text = detail.lower()
        if status in (401, 403):
            return "AUTH_FAILED"
        if status == 404:
            return "ENDPOINT_NOT_FOUND"
        if status == 429:
            return "RATE_LIMITED"
        if status in (502, 503, 529):
            return "OVERLOADED"
        if "model_not_found" in text or "model not found" in text or "not supported model" in text or "invalid model" in text:
            return "MODEL_NOT_FOUND"
        if "insufficient" in text or "balance" in text or "quota" in text or "billing" in text:
            return "BALANCE_OR_QUOTA"
        if "unsupported api" in text or "unknown parameter" in text or "protocol" in text:
            return "PROTOCOL_MISMATCH"
        return "UNKNOWN"

    def _redact_secret(self, text: str) -> str:
        return re.sub(r"(sk-|Bearer\s+)[A-Za-z0-9_\-]{12,}", r"\1***", text)

    def _extract_responses_text(self, data: Dict[str, Any]) -> str:
        output = data.get("output")
        if not isinstance(output, list):
            return ""
        parts = []
        for item in output:
            if not isinstance(item, dict):
                continue
            content_items = item.get("content")
            if not isinstance(content_items, list):
                continue
            for content_item in content_items:
                if isinstance(content_item, dict):
                    text = content_item.get("text") or content_item.get("content")
                    if text:
                        parts.append(str(text))
        return "\n".join(parts)

    def _model_available(self) -> bool:
        with httpx.Client(timeout=min(self.timeout_seconds, 5.0)) as client:
            response = client.get(f"{self.base_url}/api/tags")
            response.raise_for_status()
            models = response.json().get("models", [])
        return any(item.get("name") == self.model for item in models)

    def _system_prompt(self, capability: str) -> str:
        prompts = {
            "TEXT_CHAT": "你是超级 Agent 的文字对话助手。用简体中文直接回答，表达清晰、可执行，不暴露工具调用、Trace、日志、Provider、JSON 调试内容或后端实现细节。",
            "KNOWLEDGE_RETRIEVAL": "你是知识库问答助手。只基于给定知识片段回答；片段不足时说明不足，不编造来源。",
            "IMAGE_CREATION": "你是图片创作助手。输出可用于图像生成模型的提示词、构图、风格、色彩和负面提示词；不要声称已生成真实图片文件。",
            "VIDEO_CREATION": "你是短视频创作助手。输出脚本、分镜、口播文案、节奏建议和下一步执行建议；不要声称已生成真实视频文件。",
        }
        return prompts.get(capability, prompts["TEXT_CHAT"])

    def _user_prompt(self, capability: str, message: str, tool_context: Dict[str, Any]) -> str:
        memory_context = self._memory_context(tool_context)
        if capability == "TEXT_CHAT":
            return f"{memory_context}用户消息：{message}\n请直接回答。"

        if capability == "KNOWLEDGE_RETRIEVAL":
            hits = "\n".join(f"- {item}" for item in tool_context.get("hits", []))
            return (
                f"{memory_context}用户问题：{message}\n\n"
                f"知识片段：\n{hits or '- 无命中片段'}\n\n"
                "请基于知识片段回答；片段不足时说明不足。"
            )

        image_note = ""
        if tool_context.get("referenceImage"):
            image_note = f"\n参考图片资产 ID：{tool_context.get('referenceImage')}"

        if capability == "VIDEO_CREATION":
            return (
                f"{memory_context}用户目标：{message}{image_note}\n"
                "请输出：核心创意、30 秒脚本、分镜表、口播文案、剪辑/发布建议。"
            )

        if capability == "IMAGE_CREATION":
            return (
                f"{memory_context}用户目标：{message}{image_note}\n"
                "请输出：中文设计说明、可直接用于图像模型的提示词、风格/构图/色彩、负面提示词。"
            )

        return f"{memory_context}用户消息：{message}\n请直接回答。"

    def _memory_context(self, tool_context: Dict[str, Any]) -> str:
        summary = str(tool_context.get("conversationMemory") or "").strip()
        recent_messages = tool_context.get("recentMessages") or []
        lines = []
        if isinstance(recent_messages, list):
            for item in recent_messages[-6:]:
                if not isinstance(item, dict):
                    continue
                role = str(item.get("role") or "UNKNOWN")
                content = str(item.get("content") or "").strip()
                if content:
                    lines.append(f"{role}: {content}")
        if not summary and not lines:
            return ""
        parts = []
        if summary:
            parts.append(f"对话记忆：{summary}")
        if lines:
            parts.append("最近对话：\n" + "\n".join(lines))
        return "\n".join(parts) + "\n\n"

    def _strip_thinking(self, content: str) -> str:
        return re.sub(r"<think>.*?</think>", "", content, flags=re.DOTALL | re.IGNORECASE)

    def _local_intent_answer(
        self,
        message: str,
        tool_context: Dict[str, Any],
        provider_config: Optional[Dict[str, Any]],
    ) -> str:
        for _intent_name, matcher, handler in self._local_intent_table():
            if matcher(message):
                return handler(message, tool_context, provider_config)
        return ""

    def _local_intent_table(self):
        return (
            ("recent_user_message", self._asks_recent_user_message, self._answer_recent_user_message),
            ("current_model", self._asks_current_model, self._answer_current_model),
            ("current_config", self._asks_current_config, self._answer_current_config),
            ("capabilities", self._asks_capabilities, self._answer_capabilities),
        )

    def _answer_recent_user_message(
        self,
        message: str,
        tool_context: Dict[str, Any],
        provider_config: Optional[Dict[str, Any]] = None,
    ) -> str:
        previous = self._latest_recent_message(tool_context, "USER")
        if previous:
            return f"你刚才问的是：“{previous}”。"
        return "我还没有看到你之前的提问。"

    def _asks_current_model(self, message: str) -> bool:
        normalized = re.sub(r"\s+", "", message or "")
        return any(
            term in normalized
            for term in (
                "你是什么模型",
                "你是哪个模型",
                "你用什么模型",
                "你现在是什么模型",
                "现在用的是什么模型",
                "当前模型",
                "现在模型",
                "使用什么模型",
                "用的什么模型",
                "模型是什么",
            )
        )

    def _answer_current_model(
        self,
        message: str,
        tool_context: Dict[str, Any],
        provider_config: Optional[Dict[str, Any]] = None,
    ) -> str:
        if provider_config:
            name = str(provider_config.get("name") or "外部 Provider").strip()
            model = str(provider_config.get("model") or "未配置").strip()
            api_format = str(provider_config.get("apiFormat") or "openai_chat_completions").strip()
            return f"当前使用外部 Provider「{name}」，模型：{model}，协议：{api_format}。"
        return f"当前使用本地 Ollama，模型：{self.model}。"

    def _asks_current_config(self, message: str) -> bool:
        normalized = re.sub(r"\s+", "", message or "")
        return any(
            term in normalized
            for term in (
                "当前配置",
                "现在配置",
                "配置是什么",
                "你当前配置",
                "使用配置",
                "模型配置",
                "provider配置",
                "供应商配置",
                "api配置",
                "外部provider",
                "当前供应商",
            )
        )

    def _answer_current_config(
        self,
        message: str,
        tool_context: Dict[str, Any],
        provider_config: Optional[Dict[str, Any]] = None,
    ) -> str:
        if provider_config:
            name = str(provider_config.get("name") or "外部 Provider").strip()
            model = str(provider_config.get("model") or "未配置").strip()
            api_format = str(provider_config.get("apiFormat") or "openai_chat_completions").strip()
            base_url = str(provider_config.get("baseUrl") or "未配置").strip()
            fallback = "开启" if provider_config.get("enableFallback") else "关闭"
            return (
                f"当前配置：外部 Provider「{name}」，模型：{model}，协议：{api_format}，"
                f"Base URL：{base_url}，故障回退：{fallback}。API Key 不会在对话中显示。"
            )
        return (
            f"当前配置：本地 Ollama，模型：{self.model}，Base URL：{self.base_url}，"
            f"上下文窗口：{self.num_ctx}。"
        )

    def _asks_capabilities(self, message: str) -> bool:
        normalized = re.sub(r"\s+", "", message or "")
        return any(
            term in normalized
            for term in (
                "你能做什么",
                "你会做什么",
                "你可以做什么",
                "你有什么能力",
                "支持什么",
                "你支持",
                "能干什么",
                "可以干什么",
                "能做哪些",
                "可以做哪些",
                "你会哪些",
            )
        )

    def _answer_capabilities(
        self,
        message: str,
        tool_context: Dict[str, Any],
        provider_config: Optional[Dict[str, Any]] = None,
    ) -> str:
        return (
            "我可以处理文字问答、图片创作提示词、视频脚本/分镜、知识库问答这几类任务。"
            "当前原型主要返回文本结果；真实图片或视频文件生成需要接入对应媒体生成服务。"
        )

    def _recent_user_message_answer(self, message: str, tool_context: Dict[str, Any]) -> str:
        if not self._asks_recent_user_message(message):
            return ""
        return self._answer_recent_user_message(message, tool_context)

    def _asks_recent_user_message(self, message: str) -> bool:
        normalized = re.sub(r"\s+", "", message or "")
        if not normalized:
            return False
        question_terms = ("什么", "啥", "哪句", "哪个", "内容", "？", "?")
        if not any(term in normalized for term in question_terms):
            return False
        recent_user_terms = (
            "刚才问",
            "刚刚问",
            "刚问",
            "之前问",
            "前面问",
            "上次问",
            "上一句",
            "上句话",
            "前一句",
            "前句话",
            "上一条",
            "上条",
            "上个问题",
            "上一个问题",
            "我刚才说",
            "我刚刚说",
            "我刚才发",
            "我上一句",
            "我的上一句",
        )
        return any(term in normalized for term in recent_user_terms)

    def _latest_recent_message(self, tool_context: Dict[str, Any], role: str) -> str:
        recent_messages = tool_context.get("recentMessages") or []
        if not isinstance(recent_messages, list):
            return ""
        for item in reversed(recent_messages):
            if not isinstance(item, dict):
                continue
            if str(item.get("role") or "").upper() != role:
                continue
            content = str(item.get("content") or "").strip()
            if content:
                return content
        return ""

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
