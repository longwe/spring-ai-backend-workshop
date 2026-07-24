#!/usr/bin/env python3
"""Replaces workshop:start(id)...workshop:end regions with workshop/stubs/<id>.stub.

Used by generate-shell.sh; operates in place on the files passed as arguments.
"""
import pathlib
import re
import sys

STUB_DIR = pathlib.Path(__file__).parent / "stubs"
START = re.compile(r"workshop:start\(([^)]+)\)")


def strip(path: pathlib.Path) -> int:
    lines = path.read_text().splitlines(keepends=True)
    out, i, regions = [], 0, 0
    while i < len(lines):
        match = START.search(lines[i])
        if not match:
            out.append(lines[i])
            i += 1
            continue
        stub_file = STUB_DIR / f"{match.group(1)}.stub"
        if not stub_file.exists():
            sys.exit(f"error: {path}:{i + 1} references missing stub {stub_file.name}")
        out.append(stub_file.read_text())
        i += 1
        while i < len(lines) and "workshop:end" not in lines[i]:
            i += 1
        if i == len(lines):
            sys.exit(f"error: {path} has an unterminated workshop:start region")
        i += 1  # skip the end marker
        regions += 1
    path.write_text("".join(out))
    return regions


total = 0
for arg in sys.argv[1:]:
    total += strip(pathlib.Path(arg))
print(f"replaced {total} region(s) in {len(sys.argv) - 1} file(s)")
