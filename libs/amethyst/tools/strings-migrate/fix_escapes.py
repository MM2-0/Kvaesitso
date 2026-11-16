#!/usr/bin/env python3
"""Convert Android string-resource escaping to what Compose resources understand.

Android's aapt and Compose Multiplatform do NOT share escaping rules. Moving a
strings.xml from `res/values/` to `composeResources/values/` verbatim therefore
renders some strings wrong -- the login screen showed `Don\\'t have a Nostr account?`
with a literal backslash.

Compose 1.11.1 `handleSpecialCharacters` (compose-gradle-plugin) resolves ONLY:

    \\uXXXX, \\n, \\t     and collapses \\\\ -> \\

It does not handle these Android conventions, so this script applies them itself:

    \\'  -> '        \\"  -> "        \\?  -> ?        \\@  -> @
    "…"  -> …        (Android wraps a value in quotes to preserve leading/trailing
                      spaces; Compose would render the quotes literally)

\\n, \\t, \\uXXXX and \\\\ are deliberately left alone -- Compose already resolves
them, and converting them here would double-process.

Whitespace is the other half. aapt collapses every whitespace run in an unquoted
value to one space and trims the ends; Compose keeps the XML text verbatim. A value
wrapped over several indented lines therefore showed on Android as one line, and in
Compose as a leading line break plus eight spaces -- which CommonMark renders as a
code block (`account_backup_tips2_md`). This script applies aapt's rule to runs that
contain a line break or tab, which only XML layout produces (a deliberate newline is
written \\n). In a translation it also trims a leading/trailing space the source
string does not have: Crowdin exports a translator's stray edge space, aapt used to
drop it, Compose would draw it (" miejsca zniknęły" beside "tematy zniknęły").
Deliberate edge spaces (" and ") exist in the source too, and are kept.

Usage:  fix_escapes.py <file-or-dir> [...]        (idempotent; safe to re-run)
"""
import re
import sys
import os

STRING_EL = re.compile(r"(<(?:string|item)\b[^>]*>)(.*?)(</(?:string|item)>)", re.S)
# `tools:` attributes are an Android-lint construct. They arrive with strings moved out of
# res/values/, whose <resources> root declares xmlns:tools -- composeResources roots do not,
# so the prefix is unbound and the XML is malformed. Compose parses namespace-unaware and
# drops them silently today, but nothing should rely on that, and Android lint never runs on
# composeResources so they carry no meaning there either.
TOOLS_ATTR = re.compile(r'\s+tools:[\w.-]+="[^"]*"')
TOOLS_NS = re.compile(r'\s+xmlns:tools="[^"]*"')
# a backslash escape NOT itself preceded by a backslash
ANDROID_ONLY = re.compile(r"(?<!\\)\\(['\"?@])")
# A whitespace run that holds a line break or a tab: XML layout, never content.
LAYOUT_WS = re.compile(r"[ \t]*[\r\n\t][ \t\r\n]*")
STRING_NAME = re.compile(r'<string\b[^>]*\bname="([^"]+)"')


def normalize_whitespace(text: str, source: str = None) -> str:
    """Apply the aapt whitespace handling that Compose lacks. Idempotent.

    [source] is the default-locale value of the same key when [text] is a translation.
    """
    if "<![CDATA[" in text:
        return text
    text = LAYOUT_WS.sub(lambda m: "" if m.start() == 0 or m.end() == len(text) else " ", text)
    if source is not None:
        if not source[:1].isspace():
            text = text.lstrip(" ")
        if not source[-1:].isspace():
            text = text.rstrip(" ")
    return text


def fix_text(text: str, unwrap_quotes: bool = True, source: str = None) -> str:
    """Convert one element's text.

    NOTE: quote-unwrapping is NOT idempotent and must run exactly once per file, at
    migration time. Android wraps a value in quotes to protect whitespace, but after
    `\"` has been converted to `"` a legitimately quoted value is indistinguishable
    from a wrapped one -- a second pass strips the real quotes. Repair runs over
    already-migrated files must therefore pass unwrap_quotes=False.
    """
    if unwrap_quotes and len(text) >= 2 and text.startswith('"') and text.endswith('"'):
        # Quoted: aapt kept this whitespace exactly, so Compose keeps it too.
        return unescape_percent(ANDROID_ONLY.sub(r"\1", text[1:-1]))
    return unescape_percent(normalize_whitespace(ANDROID_ONLY.sub(r"\1", text), source))


def unescape_percent(text: str) -> str:
    """`%%` is a printf escape Compose never resolves: `%1$d%%` renders `100%%`.

    Compose substitutes only `%N$s`/`%N$d` and leaves every other `%` alone, so a
    bare `%` is already literal. Idempotent.
    """
    return text.replace("%%", "%")


def strip_android_only_attrs(src: str) -> tuple:
    """Drop `tools:` attributes (and any xmlns:tools) that mean nothing here."""
    out, n = TOOLS_ATTR.subn("", src)
    out, m = TOOLS_NS.subn("", out)
    return out, n + m


def source_values(path: str) -> dict:
    """The default-locale values beside a `values-<locale>/strings.xml`, else {}."""
    parent = os.path.dirname(os.path.abspath(path))
    if not os.path.basename(parent).startswith("values-"):
        return {}
    default = os.path.join(os.path.dirname(parent), "values", os.path.basename(path))
    if not os.path.exists(default):
        return {}
    out = {}
    for m in STRING_EL.finditer(open(default, encoding="utf-8").read()):
        name = STRING_NAME.match(m.group(1))
        if name:
            out[name.group(1)] = m.group(2)
    return out


def fix_file(path: str, unwrap_quotes: bool = True) -> int:
    src = open(path, encoding="utf-8").read()
    sources = source_values(path)
    changed = 0

    def repl(m):
        nonlocal changed
        name = STRING_NAME.match(m.group(1))
        fixed = fix_text(m.group(2), unwrap_quotes, sources.get(name.group(1)) if name else None)
        if fixed != m.group(2):
            changed += 1
        return m.group(1) + fixed + m.group(3)

    out = STRING_EL.sub(repl, src)
    out, stripped = strip_android_only_attrs(out)
    changed += stripped
    if changed:
        open(path, "w", encoding="utf-8").write(out)
    return changed


def main(paths, unwrap_quotes=True):
    total_files = total_entries = 0
    for p in paths:
        files = []
        if os.path.isdir(p):
            for root, _, names in os.walk(p):
                files += [os.path.join(root, n) for n in names if n.endswith(".xml")]
        else:
            files = [p]
        for f in sorted(files):
            n = fix_file(f, unwrap_quotes)
            if n:
                total_files += 1
                total_entries += n
                print(f"  {f}: {n} entries")
    print(f"\nfixed {total_entries} entries across {total_files} files")


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if a != "--no-unwrap-quotes"]
    if len(args) < 1:
        sys.exit(__doc__)
    main(args, unwrap_quotes="--no-unwrap-quotes" not in sys.argv)
