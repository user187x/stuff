# todo/controller.py
from __future__ import annotations
import uuid
from datetime import date, datetime
from typing import Dict
import json

from models import Project, Task
from console import print_menu, prompt, show_tasks

class TodoApp:
    """Keeps a registry of projects and drives the UI."""
    def __init__(self):
        self.projects: Dict[str, Project] = {}

    # ---- high‑level flow ----------------------------------------------
    def run(self) -> None:
        while True:
            print_menu()
            choice = prompt("Choose option")
            if choice == "1":
                self._create_project()
            elif choice == "2":
                self._add_task()
            elif choice == "3":
                self._list_tasks()
            elif choice == "4":
                self._mark_done()
            elif choice == "5":
                self._delete_task()
            elif choice == "6":
                self._pretty_print()
            elif choice == "0":
                print("Bye!")
                break
            else:
                print("Invalid option, try again.")

    # ---- individual commands ------------------------------------------
    def _create_project(self) -> None:
        name = prompt("Project name")
        if not name:
            print("Name cannot be empty.")
            return
        self.projects[name] = Project(name)
        print("Created project '{name}'")

    def _pretty_print(self) -> None:
        data_str = prompt("Pretty print JSON")
        if not data_str:
            print("Entry cannot be empty.")
            return

        try:
            # Try to interpret the input as JSON first
            data = json.loads(data_str)
        except json.JSONDecodeError:
            # If that fails, treat it simply as a string and dump it.
            data = {"input": data_str}

        # `json.dumps` returns a string; print that string.
        pretty = json.dumps(data, indent=4, sort_keys=True)
        print(pretty)

    def _add_task(self) -> None:
        proj_name = prompt("Project name")
        proj = self.projects.get(proj_name)
        if not proj:
            print(f"No such project: {proj_name}")
            return
        title = prompt("Task title")
        due_str = prompt("Due date (YYYY-MM-DD) or leave blank")
        due = None
        if due_str:
            try:
                due = datetime.strptime(due_str, "%Y-%m-%d").date()
            except ValueError:
                print("Invalid date format. Skipping due date.")
        task = Task(title, due)
        proj.add_task(task)
        print(f"Added task {task.id}")

    def _list_tasks(self) -> None:
        proj_name = prompt("Project name")
        proj = self.projects.get(proj_name)
        if not proj:
            print(f"No such project: {proj_name}")
            return
        show_completed = prompt("Show completed? (y/n)") == "y"
        tasks = proj.list_tasks(show_completed)
        show_tasks(tasks)

    def _mark_done(self) -> None:
        proj_name = prompt("Project name")
        proj = self.projects.get(proj_name)
        if not proj:
            print(f"No such project: {proj_name}")
            return
        task_id_str = prompt("Task ID to mark done")
        try:
            tid = uuid.UUID(task_id_str)
        except ValueError:
            print("Invalid UUID.")
            return
        for t in proj.tasks:
            if t.id == tid:
                t.mark_done()
                print(f"Marked task {tid} as completed.")
                break
        else:
            print("Task ID not found.")

    def _delete_task(self) -> None:
        proj_name = prompt("Project name")
        proj = self.projects.get(proj_name)
        if not proj:
            print(f"No such project: {proj_name}")
            return
        task_id_str = prompt("Task ID to delete")
        try:
            tid = uuid.UUID(task_id_str)
        except ValueError:
            print("Invalid UUID.")
            return
        if proj.remove_task(tid):
            print(f"Deleted task {tid}.")
        else:
            print("Task ID not found.")
