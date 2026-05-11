from datetime import datetime, timezone
from typing import List

from app.models.schemas import TraceEvent


class TraceRecorder:
    def __init__(self) -> None:
        self.events: List[TraceEvent] = []

    def add(self, event_type: str, message: str) -> None:
        self.events.append(
            TraceEvent(
                eventType=event_type,
                message=message,
                timestamp=datetime.now(timezone.utc).isoformat(),
            )
        )
