from typing import List

from app.models.schemas import Artifact, ToolCall


class Evaluator:
    def summarize(self, goal: str, tool_calls: List[ToolCall], llm_mode: str) -> str:
        tool_names = ", ".join(call.toolName for call in tool_calls)
        return (
            f"Agent Run completed for goal: {goal}. "
            f"LLM mode: {llm_mode}. Tools used: {tool_names}."
        )

    def summarize_business_run(
        self,
        business_module: str,
        goal: str,
        tool_calls: List[ToolCall],
        artifacts: List[Artifact],
        llm_mode: str,
    ) -> str:
        tool_names = "、".join(call.toolName for call in tool_calls)
        artifact_names = "、".join(artifact.title for artifact in artifacts)
        return (
            f"{business_module} 业务闭环已完成。目标：{goal}。"
            f"LLM 模式：{llm_mode}。调用工具：{tool_names}。"
            f"产物：{artifact_names}。"
        )
