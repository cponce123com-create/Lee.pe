"""Exercise coverage reporting with partial, malformed and boundary reports."""

from pathlib import Path
import tempfile
import unittest

from coverage_summary import summarize


class CoverageSummaryTest(unittest.TestCase):
    def test_missing_report_is_not_claimed_as_zero_coverage(self):
        with tempfile.TemporaryDirectory() as directory:
            result = summarize(Path(directory))
        self.assertIn("Informe ausente", result)
        self.assertNotIn("0.00%", result)

    def test_root_counters_are_not_summed_with_nested_counters(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path = root / "data/build/reports/kover/reportDebug.xml"
            path.parent.mkdir(parents=True)
            path.write_text(
                '<report><package><counter type="LINE" covered="100" missed="0"/></package>'
                '<counter type="LINE" covered="1" missed="1"/></report>'
            )
            self.assertIn("| Data | 1 / 2 | 50.00% | 50% | Cumplida |", summarize(root))
            path.write_text('<report><counter type="LINE" covered="0" missed="0"/></report>')
            self.assertIn("Sin líneas medibles", summarize(root))
            path.write_text("truncated xml")
            self.assertIn("Informe inválido", summarize(root))


if __name__ == "__main__":
    unittest.main()
