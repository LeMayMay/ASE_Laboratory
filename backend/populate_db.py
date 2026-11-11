#!/usr/bin/env python3
"""Wrapper that delegates to the root populate_db.py script when invoked from backend/."""

import runpy
from pathlib import Path

if __name__ == "__main__":
    root_script = Path(__file__).resolve().parent.parent / "populate_db.py"
    runpy.run_path(str(root_script), run_name="__main__")
