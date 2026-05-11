from typing import Dict


class MemoryStore:
    def recall(self, goal: str, agent_type: str) -> Dict[str, str]:
        return {
            "customer_context": "mock recent customers and conversation summary",
            "agent_preference": f"default preference for {agent_type}",
            "goal_hint": goal[:80],
        }
