#!/usr/bin/env python3
"""Packs doom.wasm and an IWAD into the single self-contained index.html a NIP-5D napplet needs.

A napplet is one HTML artifact with no network (CSP connect-src 'none'), so both binaries go in
the page as gzip+base64. Output is byte-for-byte reproducible: gzip carries no timestamp or name.

usage: assemble.py TEMPLATE WASM WAD OUT [TITLE]
"""

import base64
import gzip
import hashlib
import os
import sys
from html import escape as html_escape

# Amethyst downloads a napplet artifact only up to this size (NappletBlobHttp.MAX_BLOB_BYTES).
HOST_ARTIFACT_LIMIT = 20 * 1024 * 1024
RESX, RESY = 320, 200


def packed(path):
    with open(path, "rb") as f:
        data = f.read()
    return base64.b64encode(gzip.compress(data, compresslevel=9, mtime=0)).decode("ascii")


def main(template, wasm, wad, out, title="Freedoom"):
    with open(template, encoding="utf-8") as f:
        html = f.read()
    replacements = {
        "__WASM_GZ_BASE64__": packed(wasm),
        "__WAD_GZ_BASE64__": packed(wad),
        "__WAD_NAME__": os.path.basename(wad).lower(),
        "__TITLE__": html_escape(title),
        "__RESX__": str(RESX),
        "__RESY__": str(RESY),
    }
    for placeholder, value in replacements.items():
        if placeholder not in html:
            sys.exit(f"template is missing {placeholder}")
        html = html.replace(placeholder, value)
    data = html.encode("utf-8")
    with open(out, "wb") as f:
        f.write(data)

    print(f"{out}: {len(data):,} bytes, sha256 {hashlib.sha256(data).hexdigest()}")
    if len(data) > HOST_ARTIFACT_LIMIT:
        sys.exit(f"over Amethyst's {HOST_ARTIFACT_LIMIT:,}-byte napplet artifact limit")


if __name__ == "__main__":
    if len(sys.argv) not in (5, 6):
        sys.exit(__doc__)
    main(*sys.argv[1:])
