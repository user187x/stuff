import json
from typing import Any

class JsonPrettyPrinter:

    @staticmethod
    def print(obj: Any, indent: int = 4, sort_keys: bool = True) -> None:
        """
        Convert *obj* to JSON and write it to stdout.

        Parameters
        ----------
        obj : Any
            The object to serialise. Must be JSON‑serialisable.
        indent : int, optional (default=4)
            Number of spaces for indentation.
        sort_keys : bool, optional (default=True)
            Whether dictionary keys should be sorted alphabetically.
        """
        try:
            json_str = json.dumps(obj, indent=indent, sort_keys=sort_keys,
                                  default=str)  # fallback: use str() for non‑serialisable types
        except TypeError as exc:
            raise ValueError(f"Object of type {type(obj).__name__} is not JSON serialisable")(exc)

        print(json_str)

# ------------------------------------------------------------------
# Demo usage (run this file directly)
if __name__ == "__main__":
    sample = {
        "project": "Todo Demo",
        "tasks": [
            {"id": 1, "title": "Write code", "completed": False},
            {"id": 2, "title": "Add docs",   "completed": True}
        ],
        "created_at": "2025-08-22T12:34:56"
    }
    JsonPrettyPrinter.print(sample)