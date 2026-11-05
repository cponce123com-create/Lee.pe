"""Reject localized prose embedded in production exception/validation messages.

This narrow regression guard complements typed errors; resource-based presentation
and developer-only English invariants are not considered violations.
"""
from pathlib import Path
import re
import sys

call_patterns = (
    re.compile(r'\bIOException\s*\(\s*"([^"\n]*)"'),
    re.compile(r'\b(?:require|check)\s*\([^{}]*?\)\s*\{\s*"([^"\n]*)"', re.DOTALL),
)
localized_text = re.compile(
    r'[áéíóúñÁÉÍÓÚÑ]|\b(?:el|la|los|las|no se pudo|vac[ií]o|vac[ií]a|'
    r'colecci[oó]n|nombre|tarjeta|imagen|tama[nñ]o|p[ií]xeles|captura|'
    r'cita|coincide|p[aá]gina|v[aá]lido|positiv[oa])\b',
    re.IGNORECASE,
)
failures = []
for root in ("app", "core", "data", "feature", "library"):
    for path in Path(root).rglob("*.kt"):
        if "/src/main/" not in path.as_posix():
            continue
        source = path.read_text()
        for pattern in call_patterns:
            for match in pattern.finditer(source):
                if localized_text.search(match.group(1)):
                    line = source.count("\n", 0, match.start()) + 1
                    failures.append(f"{path}:{line}")
if failures:
    sys.exit("Use typed errors or UI resources instead of localized exception/validation literals:\n" + "\n".join(sorted(set(failures))) )
print("Exception resource guard OK")
