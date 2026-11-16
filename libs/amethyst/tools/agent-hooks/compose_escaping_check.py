#!/usr/bin/env python3
"""Fail if the Compose resource catalog carries Android-only string escaping.

Android's aapt and Compose Multiplatform do not share escaping rules. Compose
(compose-gradle-plugin `handleSpecialCharacters`) resolves only \\uXXXX, \\n and
\\t, and collapses \\\\. It leaves \\' \\" \\? \\@ alone and renders Android's
quote-wrapping literally, so a value carried verbatim out of `res/values/` ships
a visible backslash: the login screen once read `Don\\'t have a Nostr account?`.

`tools:` attributes are the same class of mistake. They are an Android-lint
construct whose namespace is declared on the Android `<resources>` root; Compose
catalog roots do not declare it, so the prefix is unbound and the XML malformed.

**This recurs on every Crowdin sync.** Crowdin holds the Android-escaped source,
so each import reintroduces it -- twice in two days, 2,068 escaped apostrophes
across 40 locales each time, always the apostrophe-heavy regional variants
(uz-rUZ, fr-rFR, fr-rCA, tr-rTR). Like the orphan-strings desync, it arrives
through a bot-authored PR with no local session in the path, so CI is the layer
that has to catch it.

Repair with:

    python3 tools/strings-migrate/fix_escapes.py --no-unwrap-quotes \\
        commonsUI/src/commonMain/composeResources

`--no-unwrap-quotes` is mandatory on already-migrated files: escape conversion is
idempotent, quote-unwrapping is not, and a second unwrap strips the real display
quotes from values like `import_follows_tips`.

Raw line breaks and tabs inside a value are the whitespace half of the same
mismatch: aapt collapsed them, Compose renders them (the same repair fixes them).

Only the Compose catalog is scanned. In an Android res tree the same escaping is
correct and must be left alone.
"""
import re
import sys
from collections import defaultdict
from pathlib import Path

CATALOG = "*/src/*/composeResources/values*/strings.xml"

# A backslash escape that is not itself escaped. \n, \t, \uXXXX and \\ are fine --
# Compose resolves those itself.
ANDROID_ESCAPE = re.compile(r"(?<!\\)\\(['\"?@])")
TOOLS_ATTR = re.compile(r'tools:[\w.-]+="')
# A value whose text holds a raw line break or tab. aapt collapsed that XML layout to
# one space; Compose draws it, and a markdown value indented by it becomes a code block.
STRING_TEXT = re.compile(r"<(string|item)\b[^>]*>(.*?)</\1>", re.S)
LAYOUT_WS = re.compile(r"[\r\n\t]")


def find_violations(root: Path):
    found = defaultdict(lambda: defaultdict(int))
    for path in sorted(root.glob(CATALOG)):
        text = path.read_text(encoding="utf-8", errors="replace")
        for esc in ANDROID_ESCAPE.findall(text):
            found[path][f"\\{esc}"] += 1
        n = len(TOOLS_ATTR.findall(text))
        if n:
            found[path]["tools: attribute"] += n
        n = sum(
            1
            for m in STRING_TEXT.finditer(text)
            if "<![CDATA[" not in m.group(2) and LAYOUT_WS.search(m.group(2))
        )
        if n:
            found[path]["raw line break/tab in a value"] += n
        # Android's printf escape. Compose substitutes only %N$s / %N$d, so `%%`
        # renders as two percent signs ("100%% uptime").
        n = sum(m.group(2).count("%%") for m in STRING_TEXT.finditer(text))
        if n:
            found[path]["%%"] += n
    return found


def main() -> int:
    root = Path(__file__).resolve().parents[2]
    found = find_violations(root)
    if not found:
        return 0

    out = sys.stderr
    total = sum(sum(k.values()) for k in found.values())
    print(
        f"Android-only escaping in the Compose resource catalog: "
        f"{total} occurrence(s) in {len(found)} file(s).",
        file=out,
    )
    print("Compose does not resolve these; they render literally.\n", file=out)
    for path, kinds in sorted(found.items(), key=lambda kv: -sum(kv[1].values()))[:12]:
        detail = ", ".join(f"{k} x{v}" for k, v in sorted(kinds.items()))
        print(f"  {path.relative_to(root)}: {detail}", file=out)
    if len(found) > 12:
        print(f"  ... and {len(found) - 12} more file(s)", file=out)
    print(
        "\nRepair:\n"
        "  python3 tools/strings-migrate/fix_escapes.py --no-unwrap-quotes \\\n"
        "      commonsUI/src/commonMain/composeResources\n"
        "(--no-unwrap-quotes is mandatory on already-migrated files.)",
        file=out,
    )
    return 1


if __name__ == "__main__":
    sys.exit(main())
