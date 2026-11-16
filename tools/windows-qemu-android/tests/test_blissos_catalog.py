#!/usr/bin/env python3
import tempfile
import unittest
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from blissos_catalog import parse_filename, parse_rss, parse_sha256_file, write_lock  # noqa: E402


RSS = """<?xml version="1.0"?>
<rss><channel>
<item><title>/Official/BlissOS16/FOSS/Generic/Bliss-v16.9.6-x86_64-OFFICIAL-foss-20240603.iso</title></item>
<item><title>/Official/BlissOS16/FOSS/Generic/Bliss-v16.9.7-x86_64-OFFICIAL-foss-20241011.iso</title></item>
<item><title>/Official/BlissOS16/FOSS/Generic/Bliss-v16.9.7-x86_64-OFFICIAL-gapps-20241011.iso</title></item>
<item><title>/Official/BlissOS16/FOSS/Go/Bliss-Go-v16.9.7-x86_64-OFFICIAL-foss-20241011.iso</title></item>
</channel></rss>
"""


class CatalogTest(unittest.TestCase):
    def test_parse_official_foss(self):
        r = parse_filename(
            "Bliss-v16.9.7-x86_64-OFFICIAL-foss-20241011.iso",
            "Official/BlissOS16/FOSS/Generic",
        )
        self.assertIsNotNone(r)
        self.assertEqual(r.version, "16.9.7")
        self.assertEqual(r.date, "20241011")

    def test_skip_gapps_and_go(self):
        self.assertIsNone(
            parse_filename(
                "Bliss-v16.9.7-x86_64-OFFICIAL-gapps-20241011.iso",
                "Official/BlissOS16/FOSS/Generic",
            )
        )
        self.assertIsNone(
            parse_filename(
                "Bliss-Go-v16.9.7-x86_64-OFFICIAL-foss-20241011.iso",
                "Official/BlissOS16/FOSS/Go",
            )
        )

    def test_rss_picks_newer(self):
        rels = parse_rss(RSS, "Official/BlissOS16/FOSS/Generic")
        rels.sort(key=lambda r: r.sort_key(), reverse=True)
        self.assertEqual(rels[0].id, "Bliss-v16.9.7-x86_64-OFFICIAL-foss-20241011")
        self.assertEqual(len(rels), 2)

    def test_sha_sidecar(self):
        self.assertEqual(
            parse_sha256_file("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa  foo.iso\n"),
            "a" * 64,
        )

    def test_lock_roundtrip(self):
        r = parse_filename(
            "Bliss-v16.9.7-x86_64-OFFICIAL-foss-20241011.iso",
            "Official/BlissOS16/FOSS/Generic",
        )
        with tempfile.TemporaryDirectory() as td:
            p = Path(td) / "blissos.lock.json"
            write_lock(p, r)
            self.assertIn("16.9.7", p.read_text())


if __name__ == "__main__":
    unittest.main()
