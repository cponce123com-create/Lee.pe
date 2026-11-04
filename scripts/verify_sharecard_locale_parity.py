#!/usr/bin/env python3
"""Fail when quote-card resources are missing from the English translation."""

from pathlib import Path
import sys
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "feature/sharecard/src/main/res/values/strings.xml"
ENGLISH = ROOT / "feature/sharecard/src/main/res/values-en/strings.xml"


def string_names(path: Path) -> set[str]:
    root = ET.parse(path).getroot()
    return {node.attrib["name"] for node in root if node.tag in {"string", "plurals", "string-array"}}


def main() -> int:
    base_names = string_names(BASE)
    translated_names = string_names(ENGLISH)
    missing = sorted(base_names - translated_names)
    extra = sorted(translated_names - base_names)
    if missing or extra:
        if missing:
            print("Missing English quote-card strings:", ", ".join(missing), file=sys.stderr)
        if extra:
            print("English-only quote-card strings:", ", ".join(extra), file=sys.stderr)
        return 1
    print(f"Quote-card locale parity OK: {len(base_names)} strings.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
