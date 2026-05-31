# todo/models.py
from __future__ import annotations
import uuid
from datetime import date, datetime
from dataclasses import dataclass, field
from typing import List

class Task:
    """A single to‑do item."""
    def __init__(self, title: str, due: date | None = None,
                 priority: int = 3, effort_hours: float | None = None):
        self.id = uuid.uuid4()
        self.title = title.strip()
        self.created_at = datetime.now()
        self.due = due

        # --- NEW FIELDS -----------------------------------------------
        self.priority = priority          # 1 (high) … 5 (low)
        self.effort_hours = effort_hours  # optional
        # --------------------------------------------------------------

        self.completed = False

    def mark_done(self) -> None:
        self.completed = True

    def __repr__(self) -> str:  # useful in debugging & UI
        status = "✓" if self.completed else "✗"
        due_str = f", due {self.due.isoformat()}" if self.due else ""
        return f"<Task {status} [{self.id}] {self.title}{due_str}>"

@dataclass
class Task:
    title: str
    due: date | None = None
    priority: int = 3
    effort_hours: float | None = None
    completed: bool = False
    id: uuid.UUID = field(default_factory=uuid.uuid4)
    created_at: datetime = field(default_factory=datetime.now)

    def mark_done(self) -> None:
        self.completed = True

class Project:
    """A collection of tasks."""
    def __init__(self, name: str):
        self.name = name.strip()
        self.tasks: List[Task] = []

    def add_task(self, task: Task) -> None:
        self.tasks.append(task)

    def remove_task(self, task_id: uuid.UUID) -> bool:
        for i, t in enumerate(self.tasks):
            if t.id == task_id:
                del self.tasks[i]
                return True
        return False

    def list_tasks(self, show_completed: bool = False) -> List[Task]:
        if show_completed:
            return self.tasks[:]
        return [t for t in self.tasks if not t.completed]

    def __repr__(self) -> str:
        return f"<Project '{self.name}' ({len(self.tasks)} tasks)>"
