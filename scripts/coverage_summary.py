"""Summarize Kover JVM line counters without treating missing reports as zero."""

import argparse
from pathlib import Path
import xml.etree.ElementTree as ET

REPORTS = (
    ("Total JVM", "build/reports/kover/report.xml", None),
    ("Domain", "core/domain/build/reports/kover/report.xml", 60),
    ("Data", "data/build/reports/kover/reportDebug.xml", 50),
    ("App", "app/build/reports/kover/reportDebug.xml", 30),
    ("Library", "feature/library/build/reports/kover/reportDebug.xml", 40),
    ("Notes", "feature/notes/build/reports/kover/reportDebug.xml", 40),
    ("Sharecard", "feature/sharecard/build/reports/kover/reportDebug.xml", 40),
)


def summarize(root: Path) -> str:
    lines = [
        "### Cobertura de líneas JVM (Kover)",
        "",
        "Las pruebas de dispositivo API 26/35 no se suman a estos porcentajes.",
        "Código generado excluido; código de producción incluido.",
        "",
        "| Módulo | Cubiertas / total | Cobertura | Meta | Estado |",
        "|---|---:|---:|---:|---|",
    ]
    for name, relative, target in REPORTS:
        path = root / relative
        goal = "—" if target is None else f"{target}%"
        if not path.exists():
            lines.append(f"| {name} | — | — | {goal} | Informe ausente |")
            continue
        try:
            counter = ET.parse(path).getroot().find("counter[@type='LINE']")
            if counter is None:
                raise ValueError("Missing LINE counter")
            covered = int(counter.attrib["covered"])
            missed = int(counter.attrib["missed"])
            if min(covered, missed) < 0:
                raise ValueError("Negative counters")
        except (ET.ParseError, KeyError, ValueError):
            lines.append(f"| {name} | — | — | {goal} | Informe inválido |")
            continue
        total = covered + missed
        if not total:
            lines.append(f"| {name} | 0 / 0 | — | {goal} | Sin líneas medibles |")
            continue
        percent = 100 * covered / total
        status = "Informativo" if target is None else ("Cumplida" if percent >= target else "Pendiente")
        lines.append(f"| {name} | {covered} / {total} | {percent:.2f}% | {goal} | {status} |")
    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path("."))
    args = parser.parse_args()
    print(summarize(args.root), end="")
