# todo/console.py
from __future__ import annotations
import sys

def print_menu() -> None:
    print("\n=== Todo Demo ===")
    print("1. Create project")
    print("2. Add task to project")
    print("3. List tasks")
    print("4. Mark task done")
    print("5. Delete task")
    print("6. Pretty Print Json")
    print("0. Exit")

def prompt(msg: str) -> str:
    return input(f"{msg}: ").strip()

def show_tasks(tasks):
    if not tasks:
        print("\nNo tasks to display.")
        return
    print("\nTasks:")
    for t in tasks:
        status = "✓" if t.completed else "✗"
        due = f", due {t.due}" if t.due else ""
        print(f"[{status}] {t.id} – {t.title}{due}")
