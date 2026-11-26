#!/usr/bin/env python3
"""
scripts/check_loc.py

Maintainability linter for source files.
Guideline: target 80-200 non-blank, non-comment SLOC.
Exceeding 200 lines triggers review; does not arbitrarily fail unless flag --strict is set.
"""

import sys
from pathlib import Path

MAX_RECOMMENDED_SLOC = 200


def count_sloc(file_path: Path) -> int:
    try:
        lines = file_path.read_text(encoding="utf-8", errors="ignore").splitlines()
    except Exception:
        return 0

    count = 0
    in_block_comment = False

    for line in lines:
        stripped = line.strip()
        if not stripped:
            continue
        if stripped.startswith("/*"):
            in_block_comment = True
        if in_block_comment:
            if "*/" in stripped:
                in_block_comment = False
            continue
        if stripped.startswith("//") or stripped.startswith("#"):
            continue
        count += 1

    return count


def main() -> int:
    workspace_root = Path(__file__).resolve().parent.parent
    strict = "--strict" in sys.argv

    scan_dirs = [
        workspace_root / "desktop" / "crates",
        workspace_root / "android",
        workspace_root / "scripts",
    ]

    extensions = {".rs", ".kt", ".py"}
    large_files: list[tuple[Path, int]] = []

    for scan_dir in scan_dirs:
        if not scan_dir.exists():
            continue
        for ext in extensions:
            for file_path in scan_dir.rglob(f"*{ext}"):
                # Skip build / target / generated folders
                if any(part in file_path.parts for part in ["target", "build", ".gradle", ".pytest_cache"]):
                    continue
                sloc = count_sloc(file_path)
                if sloc > MAX_RECOMMENDED_SLOC:
                    large_files.append((file_path.relative_to(workspace_root), sloc))

    if large_files:
        print(f"Notice: {len(large_files)} file(s) exceed {MAX_RECOMMENDED_SLOC} SLOC:")
        for path, sloc in sorted(large_files, key=lambda x: x[1], reverse=True):
            print(f"  {sloc:4d} SLOC: {path}")
        if strict:
            print("ERROR: Strict LOC check failed! Check cohesion.", file=sys.stderr)
            return 1
    else:
        print("All scanned source files are within the 200 SLOC guideline.")

    return 0


if __name__ == "__main__":
    sys.exit(main())

