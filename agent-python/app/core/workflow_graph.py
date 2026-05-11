from typing import List

from app.models.schemas import AgentStep


class WorkflowGraph:
    def build_steps(self, plan: List[str]) -> List[AgentStep]:
        return [
            AgentStep(
                stepId=f"step-{index + 1}",
                name=f"步骤 {index + 1}",
                status="pending",
                summary=item,
            )
            for index, item in enumerate(plan)
        ]
