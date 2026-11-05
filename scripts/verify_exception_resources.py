"""Reject localized literal IOException messages in production Kotlin sources.

This narrow regression guard complements typed errors; resource-based presentation
and developer-only English invariants are not considered violations.
"""
from pathlib import Path
import re
import sys

pattern = re.compile(r'IOException\(\s*"[^"\n]*[áéíóúñÁÉÍÓÚÑ]')
failures = []
for root in ("app", "core", "data", "feature", "library"):
    for path in Path(root).rglob("*.kt"):
        if "/src/main/" not in path.as_posix():
            continue
        for number, line in enumerate(path.read_text().splitlines(), 1):
            if pattern.search(line):
                failures.append(f"{path}:{number}")
if failures:
    sys.exit("Use typed errors and UI resources instead of localized IOException literals:\n" + "\n".join(failures))
print("Exception resource guard OK")
