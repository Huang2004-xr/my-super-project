from datetime import datetime, timezone
from typing import Dict, List

from app.core.capability_router import CapabilityRouter
from app.core.llm import LlmClient
from app.core.trace import TraceRecorder
from app.models.schemas import AgentRun, AgentStep, CapabilityDefinition, CreateAgentRunRequest, ImageAsset, MemoryMessage
from app.tools.registry import ToolRegistry


class AgentRuntime:
    def __init__(self) -> None:
        self.router = CapabilityRouter()
        self.tools = ToolRegistry()
        self.llm = LlmClient()
        self.runs: Dict[str, AgentRun] = {}
        self.images: Dict[str, ImageAsset] = {}

    def health(self) -> dict:
        return {"status": "ok", "service": "agent-python", "llm": self.llm.health()}

    def list_capabilities(self) -> List[CapabilityDefinition]:
        return [
            CapabilityDefinition(
                key="TEXT_CHAT",
                name="文字沟通",
                description="使用本地Ollama模型进行自由聊天、问题解释和普通文本生成。",
                examplePrompt="帮我总结一下超级Agent能做什么",
            ),
            CapabilityDefinition(
                key="VIDEO_CREATION",
                name="视频创作",
                description="使用本地Ollama模型生成短视频脚本、分镜、口播和执行建议。",
                examplePrompt="帮我生成一条介绍产品卖点的短视频脚本",
            ),
            CapabilityDefinition(
                key="IMAGE_CREATION",
                name="图片创作",
                description="使用本地Ollama模型生成图片提示词、风格、构图和负面提示词。",
                examplePrompt="帮我生成一张科技感产品海报",
            ),
            CapabilityDefinition(
                key="KNOWLEDGE_RETRIEVAL",
                name="知识库检索",
                description="先检索内置知识库，再使用本地Ollama模型生成问答结果。",
                examplePrompt="根据知识库说明，超级Agent支持哪些能力？",
            ),
        ]

    def register_image(self, file_name: str, content_type: str) -> ImageAsset:
        image = ImageAsset(fileName=file_name, contentType=content_type)
        self.images[image.imageAssetId] = image
        return image

    def summarize_memory(self, existing_summary: str, messages: List[MemoryMessage]) -> str:
        payload = [{"role": item.role, "content": item.content} for item in messages]
        return self.llm.summarize_memory(existing_summary or "", payload)

    def start_run(self, request: CreateAgentRunRequest) -> AgentRun:
        trace = TraceRecorder()
        now = datetime.now(timezone.utc)
        capability, route_reason = self.router.route(
            request.message,
            request.imageAssetId,
            request.useKnowledgeBase,
            request.capabilityHint,
        )
        run = AgentRun(
            capability=capability,
            message=request.message,
            imageAssetId=request.imageAssetId,
            useKnowledgeBase=request.useKnowledgeBase,
            knowledgeBaseId=request.knowledgeBaseId,
            routeReason=route_reason,
            status="running",
            createdAt=now,
            updatedAt=now,
        )
        self.runs[run.runId] = run

        try:
            trace.add("run_started", f"开始执行能力：{capability}")
            trace.add("capability_route", route_reason)
            trace.add("llm.provider", self.llm.provider)
            trace.add("llm.model", self.llm.model)

            steps, tool_name, artifact_type, artifact_title = self._workflow(capability)
            run.steps = steps
            payload = {
                "message": request.message,
                "imageAssetId": request.imageAssetId,
                "useKnowledgeBase": request.useKnowledgeBase,
                "knowledgeBaseId": request.knowledgeBaseId,
                **request.input,
            }
            tool_call, artifact = self.tools.execute_tool(tool_name, payload, artifact_type, artifact_title)
            artifact.data["conversationMemory"] = request.input.get("conversationMemory", "")
            artifact.data["recentMessages"] = request.input.get("recentMessages", [])
            run.toolCalls = [tool_call]
            trace.add("tool.completed", f"工具调用完成：{tool_name}")

            trace.add("llm.request", f"调用本地Ollama模型生成{capability}结果")
            llm_result = self.llm.generate(capability, request.message, artifact.data)
            trace.add("llm.response", "本地Ollama模型已返回结果")

            artifact.data["llmResult"] = llm_result
            self._apply_llm_result(capability, artifact.data, llm_result)
            run.artifacts = [artifact]
            run.finalResult = llm_result
            for step in run.steps:
                step.status = "completed"
            run.status = "completed"
            trace.add("run_completed", f"{capability} 执行完成")
        except Exception as exc:  # noqa: BLE001 - return failed run with trace to Java.
            run.status = "failed"
            run.finalResult = f"Agent Run failed: {exc}"
            trace.add("llm.error", str(exc))
            trace.add("run_failed", str(exc))
            for step in run.steps:
                if step.status == "pending":
                    step.status = "failed"
                    break
        finally:
            run.traces = trace.events
            run.updatedAt = datetime.now(timezone.utc)
            self.runs[run.runId] = run
        return run

    def get_run(self, run_id: str) -> AgentRun:
        return self.runs[run_id]

    def _workflow(self, capability: str) -> tuple[List[AgentStep], str, str, str]:
        if capability == "VIDEO_CREATION":
            return (
                [
                    self._step("route", "判断视频创作意图", "已识别为视频创作"),
                    self._step("context", "准备视频创作上下文", "整理目标、参考图和脚本结构"),
                    self._step("llm", "生成视频文本方案", "调用本地Ollama生成脚本、分镜和建议"),
                ],
                "media.plan_video",
                "video_creation",
                "视频创作结果",
            )
        if capability == "IMAGE_CREATION":
            return (
                [
                    self._step("route", "判断图片创作意图", "已识别为图片创作"),
                    self._step("context", "准备图片创作上下文", "整理目标、参考图和风格方向"),
                    self._step("llm", "生成图片提示词", "调用本地Ollama生成提示词和设计说明"),
                ],
                "media.generate_image",
                "image_creation",
                "图片创作结果",
            )
        if capability == "KNOWLEDGE_RETRIEVAL":
            return (
                [
                    self._step("route", "判断知识库检索意图", "已识别为知识库检索"),
                    self._step("search", "检索内置知识库", "命中内置知识片段"),
                    self._step("llm", "生成知识库回答", "调用本地Ollama基于片段生成回答"),
                ],
                "knowledge.search_builtin",
                "knowledge_retrieval",
                "知识库检索结果",
            )
        return (
            [
                self._step("route", "判断文字沟通意图", "已识别为文字沟通"),
                self._step("context", "准备对话上下文", "整理用户消息和可选上下文"),
                self._step("llm", "生成文字回复", "调用本地Ollama生成自然语言回复"),
            ],
            "chat.respond",
            "text_chat",
            "文字沟通结果",
        )

    def _step(self, step_id: str, name: str, summary: str) -> AgentStep:
        return AgentStep(stepId=step_id, name=name, status="pending", summary=summary)

    def _apply_llm_result(self, capability: str, data: Dict[str, object], llm_result: str) -> None:
        if capability == "VIDEO_CREATION":
            data["script"] = llm_result
            return
        if capability == "IMAGE_CREATION":
            data["prompt"] = llm_result
            return
        data["answer"] = llm_result
